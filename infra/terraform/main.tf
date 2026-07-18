module "networking" {
  source = "./modules/networking"

  project_name       = var.project_name
  environment        = var.environment
  vpc_cidr           = var.vpc_cidr
  public_subnet_cidr = var.public_subnet_cidr
  availability_zone  = var.availability_zone
}

module "security" {
  source = "./modules/security"

  project_name = var.project_name
  environment  = var.environment
  vpc_id       = module.networking.vpc_id
  your_ip_cidr = var.your_ip_cidr
}

module "compute" {
  source = "./modules/compute"

  project_name              = var.project_name
  environment               = var.environment
  aws_region                = var.aws_region
  subnet_id                 = module.networking.public_subnet_id
  jenkins_security_group_id = module.security.jenkins_sg_id
  app_security_group_id     = module.security.app_sg_id
  key_name                  = var.key_name
  instance_type_jenkins     = var.instance_type_jenkins
  instance_type_app         = var.instance_type_app
}
