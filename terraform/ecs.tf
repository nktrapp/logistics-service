# ─── CloudWatch Log Group ───
resource "aws_cloudwatch_log_group" "logistics_service" {
  name              = "/ecs/${var.project_name}/logistics-service"
  retention_in_days = 30
}

# ─── Target Group ───
resource "aws_lb_target_group" "logistics_service" {
  name        = "${var.project_name}-log-tg"
  port        = 8080
  protocol    = "HTTP"
  vpc_id      = local.vpc_id
  target_type = "ip"

  health_check {
    path                = "/management/health/liveness"
    healthy_threshold   = 2
    unhealthy_threshold = 3
    timeout             = 5
    interval            = 30
  }
}

# ─── ALB Listener Rule (attached to the base ALB active listener) ───
resource "aws_lb_listener_rule" "logistics_service" {
  listener_arn = local.alb_listener_arn
  priority     = 200

  action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.logistics_service.arn
  }

  condition {
    path_pattern {
      values = ["/api/v1/hubs*", "/api/v1/routes*"]
    }
  }
}

# ─── Task Definition ───
resource "aws_ecs_task_definition" "logistics_service" {
  family                   = "${var.project_name}-logistics-service"
  network_mode             = "awsvpc"
  requires_compatibilities = ["FARGATE"]
  cpu                      = "512"
  memory                   = "1024"
  execution_role_arn       = aws_iam_role.ecs_task_execution.arn
  task_role_arn            = aws_iam_role.ecs_task.arn

  container_definitions = jsonencode([{
    name  = "logistics-service"
    image = var.service_image
    portMappings = [{
      containerPort = 8080
      protocol      = "tcp"
    }]
    environment = [
      { name = "SPRING_PROFILES_ACTIVE", value = "prod" },
      { name = "AWS_REGION", value = var.aws_region },
      { name = "REDIS_HOST", value = aws_elasticache_cluster.main.cache_nodes[0].address },
      { name = "REDIS_PORT", value = "6379" },
      { name = "APP_MESSAGING_INBOUND_QUEUE", value = local.package_events_queue_name },
      { name = "APP_MESSAGING_OUTBOUND_QUEUE", value = local.logistics_events_queue_name },
      { name = "APP_MESSAGING_INBOUND_QUEUE_URL", value = local.package_events_queue_url },
      { name = "APP_MESSAGING_OUTBOUND_QUEUE_URL", value = local.logistics_events_queue_url },
    ]
    secrets = [
      { name = "MONGODB_URI", valueFrom = "${aws_secretsmanager_secret.mongodb.arn}:logistics_uri::" },
    ]
    logConfiguration = {
      logDriver = "awslogs"
      options = {
        "awslogs-group"         = aws_cloudwatch_log_group.logistics_service.name
        "awslogs-region"        = var.aws_region
        "awslogs-stream-prefix" = "ecs"
      }
    }
    healthCheck = {
      command     = ["CMD-SHELL", "wget --no-verbose --tries=1 --spider http://localhost:8080/management/health/liveness || exit 1"]
      interval    = 30
      timeout     = 5
      retries     = 3
      startPeriod = 60
    }
  }])
}

# ─── ECS Service ───
resource "aws_ecs_service" "logistics_service" {
  name            = "${var.project_name}-logistics-service"
  cluster         = local.ecs_cluster_id
  task_definition = aws_ecs_task_definition.logistics_service.arn
  desired_count   = var.desired_count
  launch_type     = "FARGATE"

  network_configuration {
    subnets          = local.private_subnet_ids
    security_groups  = [local.ecs_security_group_id]
    assign_public_ip = false
  }

  load_balancer {
    target_group_arn = aws_lb_target_group.logistics_service.arn
    container_name   = "logistics-service"
    container_port   = 8080
  }

  depends_on = [aws_lb_listener_rule.logistics_service]
}

# ─── Autoscaling (target-tracking on CPU) ───
resource "aws_appautoscaling_target" "logistics_service" {
  max_capacity       = var.max_capacity
  min_capacity       = var.min_capacity
  resource_id        = "service/${local.ecs_cluster_name}/${aws_ecs_service.logistics_service.name}"
  scalable_dimension = "ecs:service:DesiredCount"
  service_namespace  = "ecs"
}

resource "aws_appautoscaling_policy" "logistics_service_cpu" {
  name               = "${var.project_name}-logistics-service-cpu-tt"
  policy_type        = "TargetTrackingScaling"
  resource_id        = aws_appautoscaling_target.logistics_service.resource_id
  scalable_dimension = aws_appautoscaling_target.logistics_service.scalable_dimension
  service_namespace  = aws_appautoscaling_target.logistics_service.service_namespace

  target_tracking_scaling_policy_configuration {
    predefined_metric_specification {
      predefined_metric_type = "ECSServiceAverageCPUUtilization"
    }
    target_value       = var.cpu_target_value
    scale_in_cooldown  = 60
    scale_out_cooldown = 60
  }
}
