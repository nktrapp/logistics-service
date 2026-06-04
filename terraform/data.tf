data "aws_caller_identity" "current" {}

data "terraform_remote_state" "base" {
  backend = "s3"

  config = {
    bucket = var.terraform_state_bucket
    key    = "base/terraform.tfstate"
    region = var.aws_region
  }
}

locals {
  contracts_sqs_ssm_prefix = "${var.contracts_ssm_root_prefix}/${var.environment}/contracts/sqs"
}

data "aws_ssm_parameter" "package_events_name" {
  name = "${local.contracts_sqs_ssm_prefix}/package-events/name"
}

data "aws_ssm_parameter" "package_events_url" {
  name = "${local.contracts_sqs_ssm_prefix}/package-events/url"
}

data "aws_ssm_parameter" "package_events_arn" {
  name = "${local.contracts_sqs_ssm_prefix}/package-events/arn"
}

data "aws_ssm_parameter" "logistics_events_name" {
  name = "${local.contracts_sqs_ssm_prefix}/logistics-events/name"
}

data "aws_ssm_parameter" "logistics_events_url" {
  name = "${local.contracts_sqs_ssm_prefix}/logistics-events/url"
}

data "aws_ssm_parameter" "logistics_events_arn" {
  name = "${local.contracts_sqs_ssm_prefix}/logistics-events/arn"
}

locals {
  vpc_id                = data.terraform_remote_state.base.outputs.vpc_id
  private_subnet_ids    = data.terraform_remote_state.base.outputs.private_subnet_ids
  ecs_cluster_id        = data.terraform_remote_state.base.outputs.ecs_cluster_id
  ecs_cluster_name      = data.terraform_remote_state.base.outputs.ecs_cluster_name
  ecs_capacity_provider = data.terraform_remote_state.base.outputs.ecs_capacity_provider_name
  ecs_security_group_id = data.terraform_remote_state.base.outputs.ecs_security_group_id
  alb_listener_arn      = data.terraform_remote_state.base.outputs.alb_active_listener_arn

  package_events_queue_name   = data.aws_ssm_parameter.package_events_name.value
  package_events_queue_url    = data.aws_ssm_parameter.package_events_url.value
  package_events_queue_arn    = data.aws_ssm_parameter.package_events_arn.value
  logistics_events_queue_name = data.aws_ssm_parameter.logistics_events_name.value
  logistics_events_queue_url  = data.aws_ssm_parameter.logistics_events_url.value
  logistics_events_queue_arn  = data.aws_ssm_parameter.logistics_events_arn.value
}
