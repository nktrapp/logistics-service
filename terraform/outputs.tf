output "logistics_service_url" {
  description = "Logistics Service URL via the shared ALB"
  value       = "http://${data.terraform_remote_state.base.outputs.alb_dns_name}/api/v1/hubs"
}

output "ecr_repository_url" {
  description = "Logistics Service ECR repository URL"
  value       = aws_ecr_repository.logistics_service.repository_url
}

output "logistics_events_queue_url" {
  description = "logistics-events-queue.fifo URL"
  value       = aws_sqs_queue.logistics_events.url
}

output "logistics_events_queue_arn" {
  description = "logistics-events-queue.fifo ARN"
  value       = aws_sqs_queue.logistics_events.arn
}

output "documentdb_endpoint" {
  description = "DocumentDB cluster endpoint"
  value       = aws_docdb_cluster.main.endpoint
}

output "elasticache_endpoint" {
  description = "ElastiCache (Redis) endpoint"
  value       = aws_elasticache_cluster.main.cache_nodes[0].address
}

output "mongodb_secret_arn" {
  description = "MongoDB secret ARN"
  value       = aws_secretsmanager_secret.mongodb.arn
}

output "redis_secret_arn" {
  description = "Redis secret ARN"
  value       = aws_secretsmanager_secret.redis.arn
}
