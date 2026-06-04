resource "aws_ecr_repository" "logistics_service" {
  name                 = "${var.project_name}/logistics-service"
  image_tag_mutability = "MUTABLE"

  image_scanning_configuration {
    scan_on_push = false
  }

  encryption_configuration {
    encryption_type = "AES256"
  }

  tags = {
    Service = "logistics-service"
  }
}

resource "aws_ecr_lifecycle_policy" "logistics_service" {
  repository = aws_ecr_repository.logistics_service.name

  policy = jsonencode({
    rules = [{
      rulePriority = 1
      description  = "Keep last 2 images"
      selection = {
        tagStatus   = "any"
        countType   = "imageCountMoreThan"
        countNumber = 2
      }
      action = {
        type = "expire"
      }
    }]
  })
}
