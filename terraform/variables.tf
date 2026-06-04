variable "aws_region" {
  description = "AWS region"
  type        = string
  default     = "us-east-1"
}

variable "environment" {
  description = "Environment name"
  type        = string
  default     = "prod"
}

variable "project_name" {
  description = "Project name prefix"
  type        = string
  default     = "furb-logistics"
}

variable "service_image" {
  description = "Docker image for logistics-service"
  type        = string
}

variable "docdb_master_username" {
  description = "DocumentDB master username"
  type        = string
  default     = "docdbadmin"
  sensitive   = true
}

variable "docdb_master_password" {
  description = "DocumentDB master password"
  type        = string
  sensitive   = true
}

variable "docdb_instance_count" {
  description = "Number of DocumentDB cluster instances (writer + replicas)"
  type        = number
  default     = 1
}

variable "desired_count" {
  description = "Desired number of ECS tasks for the service"
  type        = number
  default     = 1
}

variable "min_capacity" {
  description = "Minimum number of tasks for autoscaling"
  type        = number
  default     = 1
}

variable "max_capacity" {
  description = "Maximum number of tasks for autoscaling"
  type        = number
  default     = 1
}

variable "cpu_target_value" {
  description = "Target average CPU utilization (percent) for autoscaling"
  type        = number
  default     = 60
}

variable "create_cloudwatch_alarms" {
  description = "Create CloudWatch alarms and SNS topic for this service."
  type        = bool
  default     = false
}

variable "terraform_state_bucket" {
  description = "S3 bucket holding the base stack remote state"
  type        = string
  default     = "furb-logistics-terraform-state"
}

variable "contracts_ssm_root_prefix" {
  description = "Root SSM prefix published by logistic-iac contracts"
  type        = string
  default     = "/logistic"
}
