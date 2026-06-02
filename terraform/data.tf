data "aws_caller_identity" "current" {}

# ─── Base stack (shared infrastructure) ───
data "terraform_remote_state" "base" {
  backend = "s3"

  config = {
    bucket = var.terraform_state_bucket
    key    = "base/terraform.tfstate"
    region = var.aws_region
  }
}

# ─── Peer queue created by the package-service stack ───
# logistics-service CONSUMES package-events-queue.fifo. The queue is owned by
# the package stack; we reference it here for IAM grants and the task env.
data "aws_sqs_queue" "package_events" {
  name = "${var.project_name}-package-events-queue.fifo"
}

locals {
  vpc_id                = data.terraform_remote_state.base.outputs.vpc_id
  private_subnet_ids    = data.terraform_remote_state.base.outputs.private_subnet_ids
  ecs_cluster_id        = data.terraform_remote_state.base.outputs.ecs_cluster_id
  ecs_cluster_name      = data.terraform_remote_state.base.outputs.ecs_cluster_name
  ecs_security_group_id = data.terraform_remote_state.base.outputs.ecs_security_group_id
  alb_listener_arn      = data.terraform_remote_state.base.outputs.alb_active_listener_arn
}
