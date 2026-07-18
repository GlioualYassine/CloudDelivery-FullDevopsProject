variable "project_name" {
  type = string
}

variable "environment" {
  type = string
}

variable "aws_region" {
  type = string
}

variable "subnet_id" {
  type = string
}

variable "jenkins_security_group_id" {
  type = string
}

variable "app_security_group_id" {
  type = string
}

variable "key_name" {
  type = string
}

variable "instance_type_jenkins" {
  type = string
}

variable "instance_type_app" {
  type = string
}
