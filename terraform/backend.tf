terraform {
  backend "s3" {
    bucket         = "furb-logistics-terraform-state"
    key            = "logistics-service/terraform.tfstate"
    region         = "us-east-1"
    dynamodb_table = "terraform-lock"
    encrypt        = true
  }
}
