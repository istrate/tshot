#!/bin/bash

# MongoDB Troubleshooting Tool - Build and Push Script
# This script builds the Docker image and pushes it to quay.io

set -e

# Configuration
IMAGE_NAME="mongo-troubleshoot"
REGISTRY="quay.io"
NAMESPACE="${QUAY_NAMESPACE:-istrate}"
TAG="${IMAGE_TAG:-latest}"
FULL_IMAGE_NAME="${REGISTRY}/${NAMESPACE}/${IMAGE_NAME}:${TAG}"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Function to print colored messages
print_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

print_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Function to check if required tools are installed
check_prerequisites() {
    print_info "Checking prerequisites..."

    if ! command -v docker &> /dev/null; then
        print_error "Docker is not installed. Please install Docker first."
        exit 1
    fi

    if ! command -v mvn &> /dev/null; then
        print_warn "Maven is not installed locally, but it will be available in the Docker build."
    fi

    print_info "Prerequisites check completed."
}

# Function to validate environment variables
validate_env() {
    print_info "Validating environment variables..."

    if [ -z "$QUAY_NAMESPACE" ]; then
        print_warn "QUAY_NAMESPACE not set. Using default: 'your-namespace'"
        print_warn "Set it with: export QUAY_NAMESPACE=your-namespace"
    fi

    if [ -z "$QUAY_USERNAME" ]; then
        print_warn "QUAY_USERNAME not set. You may need to login manually."
    fi

    if [ -z "$QUAY_PASSWORD" ]; then
        print_warn "QUAY_PASSWORD not set. You may need to login manually."
    fi
}

# Function to login to quay.io
login_to_registry() {
    print_info "Logging in to ${REGISTRY}..."

    if [ -n "$QUAY_USERNAME" ] && [ -n "$QUAY_PASSWORD" ]; then
        echo "$QUAY_PASSWORD" | docker login ${REGISTRY} -u "$QUAY_USERNAME" --password-stdin
        print_info "Successfully logged in to ${REGISTRY}"
    else
        print_warn "Please login manually:"
        docker login ${REGISTRY}
    fi
}

# Function to build the Docker image
build_image() {
    print_info "Building Docker image: ${FULL_IMAGE_NAME}"
    print_info "This may take several minutes..."

    # Build with the specified tag
    docker build \
        --tag ${FULL_IMAGE_NAME} \
        --tag ${REGISTRY}/${NAMESPACE}/${IMAGE_NAME}:$(date +%Y%m%d-%H%M%S) \
        --build-arg BUILD_DATE=$(date -u +'%Y-%m-%dT%H:%M:%SZ') \
        --build-arg VCS_REF=$(git rev-parse --short HEAD 2>/dev/null || echo "unknown") \
        .

    if [ $? -eq 0 ]; then
        print_info "Docker image built successfully!"

        # If a custom tag was specified (not "latest"), also tag with "latest"
        if [ "$TAG" != "latest" ]; then
            print_info "Tagging image with 'latest' tag as well..."
            docker tag ${FULL_IMAGE_NAME} ${REGISTRY}/${NAMESPACE}/${IMAGE_NAME}:latest
            print_info "Image tagged with both '${TAG}' and 'latest'"
        fi
    else
        print_error "Docker build failed!"
        exit 1
    fi
}

# Function to test the image locally
test_image() {
    print_info "Testing the Docker image locally..."

    # Stop any existing container
    docker stop mongo-troubleshoot-test 2>/dev/null || true
    docker rm mongo-troubleshoot-test 2>/dev/null || true

    # Run the container
    print_info "Starting container on port 9080..."
    docker run -d \
        --name mongo-troubleshoot-test \
        -p 9080:9080 \
        ${FULL_IMAGE_NAME}

    # Wait for the application to start
    print_info "Waiting for application to start (30 seconds)..."
    sleep 30

    # Test the health endpoint
    if curl -f http://localhost:9080/health &> /dev/null; then
        print_info "Health check passed!"
        print_info "Application is running at: http://localhost:9080"
        print_info "To stop the test container, run: docker stop mongo-troubleshoot-test"
    else
        print_warn "Health check failed. Check container logs with: docker logs mongo-troubleshoot-test"
    fi
}

# Function to push the image to registry
push_image() {
    print_info "Pushing image to ${REGISTRY}..."

    # Push the specified tag
    docker push ${FULL_IMAGE_NAME}

    if [ $? -ne 0 ]; then
        print_error "Failed to push image with tag '${TAG}'!"
        exit 1
    fi

    print_info "Image pushed successfully: ${FULL_IMAGE_NAME}"

    # If a custom tag was specified (not "latest"), also push the "latest" tag
    if [ "$TAG" != "latest" ]; then
        print_info "Pushing 'latest' tag as well..."
        LATEST_IMAGE_NAME="${REGISTRY}/${NAMESPACE}/${IMAGE_NAME}:latest"
        docker push ${LATEST_IMAGE_NAME}

        if [ $? -eq 0 ]; then
            print_info "Image pushed successfully: ${LATEST_IMAGE_NAME}"
        else
            print_error "Failed to push 'latest' tag!"
            exit 1
        fi
    fi

    # Also push the timestamped tag
    TIMESTAMP_TAG=${REGISTRY}/${NAMESPACE}/${IMAGE_NAME}:$(date +%Y%m%d-%H%M%S)
    docker push ${TIMESTAMP_TAG} 2>/dev/null || true

    print_info "All images pushed successfully!"
}

# Function to check if logged into OpenShift/Kubernetes
check_cluster_login() {
    print_info "Checking cluster login status..."

    if ! command -v oc &> /dev/null && ! command -v kubectl &> /dev/null; then
        print_warn "Neither 'oc' nor 'kubectl' command found. Skipping deployment rollout."
        return 1
    fi

    # Try oc first (OpenShift), then kubectl
    if command -v oc &> /dev/null; then
        if oc whoami &> /dev/null; then
            print_info "Logged into OpenShift as: $(oc whoami)"
            print_info "Current project: $(oc project -q 2>/dev/null || echo 'default')"
            return 0
        else
            print_warn "Not logged into OpenShift cluster."
            return 1
        fi
    elif command -v kubectl &> /dev/null; then
        if kubectl cluster-info &> /dev/null; then
            print_info "Connected to Kubernetes cluster"
            print_info "Current context: $(kubectl config current-context 2>/dev/null || echo 'unknown')"
            print_info "Current namespace: $(kubectl config view --minify -o jsonpath='{..namespace}' 2>/dev/null || echo 'default')"
            return 0
        else
            print_warn "Not connected to Kubernetes cluster."
            return 1
        fi
    fi

    return 1
}

# Function to check if tshot deployment exists
check_deployment() {
    local deployment_name="tshot"

    print_info "Checking for '${deployment_name}' deployment..."

    if command -v oc &> /dev/null && oc whoami &> /dev/null; then
        if oc get deployment ${deployment_name} >/dev/null 2>&1; then
            print_info "Found deployment: ${deployment_name}"
            return 0
        else
            print_warn "Deployment '${deployment_name}' not found in current project."
            return 1
        fi
    elif command -v kubectl &> /dev/null && kubectl cluster-info &> /dev/null; then
        if kubectl get deployment ${deployment_name} >/dev/null 2>&1; then
            print_info "Found deployment: ${deployment_name}"
            return 0
        else
            print_warn "Deployment '${deployment_name}' not found in current namespace."
            return 1
        fi
    fi

    return 1
}

# Function to rollout deployment
rollout_deployment() {
    local deployment_name="tshot"

    print_info "Triggering rollout for deployment: ${deployment_name}"

    if command -v oc &> /dev/null && oc whoami &> /dev/null; then
        print_info "Using OpenShift CLI (oc)..."
        
        # Update the image - capture output and filter error messages
        local set_image_output
        set_image_output=$(oc set image deployment/${deployment_name} ${deployment_name}=${FULL_IMAGE_NAME} 2>&1)
        local set_image_status=$?
        
        # Filter out the "unable to find container" error and show as warning
        if echo "$set_image_output" | grep -q "unable to find container"; then
            print_warn "Container '${deployment_name}' not found in deployment spec, but deployment will be restarted with new image"
        elif [ $set_image_status -ne 0 ]; then
            print_error "Failed to set image: $set_image_output"
            return 1
        else
            print_info "Image updated successfully"
        fi
        
        # Trigger rollout
        oc rollout restart deployment/${deployment_name} >/dev/null 2>&1
        
        print_info "Waiting for rollout to complete..."
        if oc rollout status deployment/${deployment_name} --timeout=5m; then
            print_info "Rollout completed successfully!"
            
            # Show pod status
            print_info "Current pod status:"
            oc get pods -l app=${deployment_name}
        else
            print_error "Rollout failed or timed out!"
            print_error "Check status with: oc rollout status deployment/${deployment_name}"
            return 1
        fi
        
    elif command -v kubectl &> /dev/null && kubectl cluster-info &> /dev/null; then
        print_info "Using Kubernetes CLI (kubectl)..."
        
        # Update the image - capture output and filter error messages
        local set_image_output
        set_image_output=$(kubectl set image deployment/${deployment_name} ${deployment_name}=${FULL_IMAGE_NAME} 2>&1)
        local set_image_status=$?
        
        # Filter out the "unable to find container" error and show as warning
        if echo "$set_image_output" | grep -q "unable to find container"; then
            print_warn "Container '${deployment_name}' not found in deployment spec, but deployment will be restarted with new image"
        elif [ $set_image_status -ne 0 ]; then
            print_error "Failed to set image: $set_image_output"
            return 1
        else
            print_info "Image updated successfully"
        fi
        
        # Trigger rollout
        kubectl rollout restart deployment/${deployment_name} >/dev/null 2>&1
        
        print_info "Waiting for rollout to complete..."
        if kubectl rollout status deployment/${deployment_name} --timeout=5m; then
            print_info "Rollout completed successfully!"
            
            # Show pod status
            print_info "Current pod status:"
            kubectl get pods -l app=${deployment_name}
        else
            print_error "Rollout failed or timed out!"
            print_error "Check status with: kubectl rollout status deployment/${deployment_name}"
            return 1
        fi
    fi

    return 0
}

# Function to display usage information
show_usage() {
    cat << EOF
Usage: $0 [OPTIONS]

Build and push MongoDB Troubleshooting Tool Docker image to quay.io

OPTIONS:
    -h, --help          Show this help message
    -b, --build-only    Only build the image, don't push
    -t, --test          Test the image locally after building
    -s, --skip-login    Skip registry login (use if already logged in)
    -r, --rollout       Trigger deployment rollout after push (requires cluster login)
    --tag TAG           Specify image tag (default: latest)

ENVIRONMENT VARIABLES:
    QUAY_NAMESPACE      Quay.io namespace (required)
    QUAY_USERNAME       Quay.io username (optional, for auto-login)
    QUAY_PASSWORD       Quay.io password (optional, for auto-login)
    IMAGE_TAG           Image tag (default: latest)

EXAMPLES:
    # Build and push with auto-login
    export QUAY_NAMESPACE=myorg
    export QUAY_USERNAME=myuser
    export QUAY_PASSWORD=mypass
    $0

    # Build only
    $0 --build-only

    # Build, test locally, then push
    $0 --test

    # Build, push, and rollout deployment
    $0 --rollout

    # Use custom tag
    $0 --tag v1.0.0

EOF
}

# Main script
main() {
    local BUILD_ONLY=false
    local TEST_LOCAL=false
    local SKIP_LOGIN=false
    local DO_ROLLOUT=false

    # Parse command line arguments
    while [[ $# -gt 0 ]]; do
        case $1 in
            -h|--help)
                show_usage
                exit 0
                ;;
            -b|--build-only)
                BUILD_ONLY=true
                shift
                ;;
            -t|--test)
                TEST_LOCAL=true
                shift
                ;;
            -s|--skip-login)
                SKIP_LOGIN=true
                shift
                ;;
            -r|--rollout)
                DO_ROLLOUT=true
                shift
                ;;
            --tag)
                TAG="$2"
                FULL_IMAGE_NAME="${REGISTRY}/${NAMESPACE}/${IMAGE_NAME}:${TAG}"
                shift 2
                ;;
            *)
                print_error "Unknown option: $1"
                show_usage
                exit 1
                ;;
        esac
    done

    print_info "=== MongoDB Troubleshooting Tool - Build and Push ==="
    print_info "Image: ${FULL_IMAGE_NAME}"
    echo ""

    # Run checks
    check_prerequisites
    validate_env

    # Login to registry if not skipped and not build-only
    if [ "$SKIP_LOGIN" = false ] && [ "$BUILD_ONLY" = false ]; then
        login_to_registry
    fi

    # Build the image
    build_image

    # Test locally if requested
    if [ "$TEST_LOCAL" = true ]; then
        test_image
    fi

    # Push the image if not build-only
    if [ "$BUILD_ONLY" = false ]; then
        push_image
        print_info "=== Build and Push Completed Successfully ==="

        # Perform rollout if requested
        if [ "$DO_ROLLOUT" = true ]; then
            echo ""
            print_info "=== Starting Deployment Rollout ==="

            if check_cluster_login; then
                if check_deployment; then
                    if rollout_deployment; then
                        print_info "=== Deployment Rollout Completed Successfully ==="
                    else
                        print_error "=== Deployment Rollout Failed ==="
                        exit 1
                    fi
                else
                    print_warn "Skipping rollout - deployment 'tshot' not found"
                    print_info "To create the deployment, use: kubectl create deployment tshot --image=${FULL_IMAGE_NAME}"
                fi
            else
                print_warn "Skipping rollout - not logged into cluster"
                print_info "Login with: oc login <cluster-url> or kubectl config use-context <context>"
            fi
        fi
    else
        print_info "=== Build Completed Successfully ==="
        print_info "Image not pushed (build-only mode)"
    fi

    echo ""
    print_info "To run the image:"
    print_info "  docker run -p 9080:9080 ${FULL_IMAGE_NAME}"
    echo ""
    print_info "To deploy to Kubernetes/OpenShift:"
    print_info "  kubectl create deployment tshot --image=${FULL_IMAGE_NAME}"
    print_info "  kubectl expose deployment tshot --type=LoadBalancer --port=80 --target-port=9080"
    echo ""
    print_info "To trigger a rollout manually:"
    print_info "  oc rollout restart deployment/tshot"
    print_info "  or"
    print_info "  kubectl rollout restart deployment/tshot"
}

# Run main function
main "$@"