resource "aws_security_group" "docdb" {
  name_prefix = "${var.project_name}-log-docdb-"
  vpc_id      = local.vpc_id

  ingress {
    from_port       = 27017
    to_port         = 27017
    protocol        = "tcp"
    security_groups = [local.ecs_security_group_id]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "${var.project_name}-log-docdb-sg"
  }
}

resource "aws_docdb_subnet_group" "main" {
  name       = "${var.project_name}-log-docdb-subnet"
  subnet_ids = local.private_subnet_ids

  tags = {
    Name = "${var.project_name}-log-docdb-subnet"
  }
}

resource "aws_docdb_cluster_parameter_group" "main" {
  family = "docdb5.0"
  name   = "${var.project_name}-log-docdb-params"

  parameter {
    name  = "tls"
    value = "enabled"
  }
}

resource "aws_docdb_cluster" "main" {
  cluster_identifier              = "${var.project_name}-log-docdb"
  engine                          = "docdb"
  master_username                 = var.docdb_master_username
  master_password                 = var.docdb_master_password
  backup_retention_period         = 1
  preferred_backup_window         = "03:00-04:00"
  skip_final_snapshot             = true
  db_subnet_group_name            = aws_docdb_subnet_group.main.name
  vpc_security_group_ids          = [aws_security_group.docdb.id]
  db_cluster_parameter_group_name = aws_docdb_cluster_parameter_group.main.name
  storage_encrypted               = true

  tags = {
    Name = "${var.project_name}-log-docdb"
  }
}

resource "aws_docdb_cluster_instance" "main" {
  count              = var.docdb_instance_count
  identifier         = "${var.project_name}-log-docdb-instance-${count.index + 1}"
  cluster_identifier = aws_docdb_cluster.main.id
  instance_class     = "db.t3.medium"
}
