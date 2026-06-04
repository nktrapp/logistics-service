resource "aws_sns_topic" "alarms" {
  name = "${var.project_name}-logistics-service-alarms"
}

resource "aws_cloudwatch_metric_alarm" "logistics_cpu_high" {
  alarm_name          = "${var.project_name}-logistics-service-cpu-high"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 2
  metric_name         = "CPUUtilization"
  namespace           = "AWS/ECS"
  period              = 300
  statistic           = "Average"
  threshold           = 80
  alarm_description   = "Logistics Service CPU > 80%"
  alarm_actions       = [aws_sns_topic.alarms.arn]

  dimensions = {
    ClusterName = local.ecs_cluster_name
    ServiceName = aws_ecs_service.logistics_service.name
  }
}

resource "aws_cloudwatch_metric_alarm" "logistics_memory_high" {
  alarm_name          = "${var.project_name}-logistics-service-memory-high"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 2
  metric_name         = "MemoryUtilization"
  namespace           = "AWS/ECS"
  period              = 300
  statistic           = "Average"
  threshold           = 85
  alarm_description   = "Logistics Service Memory > 85%"
  alarm_actions       = [aws_sns_topic.alarms.arn]

  dimensions = {
    ClusterName = local.ecs_cluster_name
    ServiceName = aws_ecs_service.logistics_service.name
  }
}
