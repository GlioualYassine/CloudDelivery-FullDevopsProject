output "jenkins_public_ip" {
  description = "Public IP of the Jenkins EC2 instance"
  value       = module.compute.jenkins_public_ip
}

output "app_public_ip" {
  description = "Public IP of the App EC2 instance"
  value       = module.compute.app_public_ip
}

output "jenkins_url" {
  description = "Jenkins web UI URL"
  value       = "http://${module.compute.jenkins_public_ip}:8080"
}

output "app_url" {
  description = "Application API Gateway URL"
  value       = "http://${module.compute.app_public_ip}:8080"
}

output "ssh_jenkins" {
  description = "SSH command to connect to Jenkins server"
  value       = "ssh -i ~/.ssh/${var.key_name}.pem ubuntu@${module.compute.jenkins_public_ip}"
}

output "ssh_app" {
  description = "SSH command to connect to App server"
  value       = "ssh -i ~/.ssh/${var.key_name}.pem ubuntu@${module.compute.app_public_ip}"
}
