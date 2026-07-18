output "jenkins_sg_id" {
  value = aws_security_group.jenkins.id
}

output "app_sg_id" {
  value = aws_security_group.app.id
}
