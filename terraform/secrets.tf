resource "aws_secretsmanager_secret" "mongodb" {
  name = "${var.project_name}/${var.environment}/logistics-service/mongodb"

  tags = {
    Name = "${var.project_name}-logistics-mongodb-secret"
  }
}

resource "aws_secretsmanager_secret_version" "mongodb" {
  secret_id = aws_secretsmanager_secret.mongodb.id
  secret_string = jsonencode({
    username      = var.docdb_master_username
    password      = var.docdb_master_password
    endpoint      = aws_docdb_cluster.main.endpoint
    uri           = "mongodb://${var.docdb_master_username}:${var.docdb_master_password}@${aws_docdb_cluster.main.endpoint}:27017/?tls=true&replicaSet=rs0&readPreference=secondaryPreferred&retryWrites=false&authSource=admin"
    logistics_uri = "mongodb://${var.docdb_master_username}:${var.docdb_master_password}@${aws_docdb_cluster.main.endpoint}:27017/logistics_db?tls=true&replicaSet=rs0&readPreference=secondaryPreferred&retryWrites=false&authSource=admin"
  })
}

resource "aws_secretsmanager_secret" "redis" {
  name = "${var.project_name}/${var.environment}/logistics-service/redis"

  tags = {
    Name = "${var.project_name}-logistics-redis-secret"
  }
}

resource "aws_secretsmanager_secret_version" "redis" {
  secret_id = aws_secretsmanager_secret.redis.id
  secret_string = jsonencode({
    endpoint = aws_elasticache_cluster.main.cache_nodes[0].address
  })
}
