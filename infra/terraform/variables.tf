variable "aws_region" {
  description = "AWS region to deploy into"
  type        = string
  default     = "eu-west-3"
}

variable "availability_zone" {
  description = "Availability zone within the region"
  type        = string
  default     = "eu-west-3a"
}

variable "vpc_cidr" {
  description = "CIDR block for the VPC"
  type        = string
  default     = "10.0.0.0/16"
}

variable "public_subnet_cidr" {
  description = "CIDR block for the public subnet"
  type        = string
  default     = "10.0.1.0/24"
}

variable "key_name" {
  description = "Name of the existing AWS EC2 Key Pair for SSH access"
  type        = string
}

variable "your_ip_cidr" {
  description = "Your public IP in CIDR notation for SSH access (e.g. 1.2.3.4/32)"
  type        = string
}

variable "instance_type_jenkins" {
  description = "EC2 instance type for Jenkins server"
  type        = string
  default     = "t3.medium"
}

variable "instance_type_app" {
  description = "EC2 instance type for App server (Docker Compose)"
  type        = string
  default     = "t3.medium"
}

variable "project_name" {
  description = "Project prefix applied to all resource names"
  type        = string
  default     = "clouddelivery"
}

variable "environment" {
  description = "Deployment environment tag"
  type        = string
  default     = "staging"
}
