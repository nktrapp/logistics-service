# ─── Logistics Events Queue (FIFO) ───
# Owned by the logistics-service stack. logistics-service PUBLISHES here; the
# package-service stack CONSUMES it via a data lookup.
resource "aws_sqs_queue" "logistics_events_dlq" {
  name                        = "${var.project_name}-logistics-events-dlq.fifo"
  fifo_queue                  = true
  content_based_deduplication = false
  message_retention_seconds   = 1209600 # 14 days

  tags = {
    Service = "logistics-service"
    Type    = "dlq"
  }
}

resource "aws_sqs_queue" "logistics_events" {
  name                        = "${var.project_name}-logistics-events-queue.fifo"
  fifo_queue                  = true
  content_based_deduplication = false
  visibility_timeout_seconds  = 60
  message_retention_seconds   = 345600 # 4 days
  receive_wait_time_seconds   = 20

  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.logistics_events_dlq.arn
    maxReceiveCount     = 3
  })

  tags = {
    Service = "logistics-service"
    Type    = "main"
  }
}
