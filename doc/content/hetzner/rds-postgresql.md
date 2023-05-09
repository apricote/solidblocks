---
title: RDS PostgreSQL
weight: 50
---

Based on the [RDS PostgreSQL]({{%relref "rds/_index.md" %}}) docker image this Terraform module provides a
ready to use PostgreSQL server that is backed up to a S3 compatible object store.

The module supports two backup methods either local storage (Hetzner cloud Volume) or a S3 compatible object store. To
avoid data loss it is not possible to run the database without one of the backup methods enabled.

<!-- BEGIN_TF_DOCS -->
## Requirements

| Name | Version |
|------|---------|
| <a name="requirement_hcloud"></a> [hcloud](#requirement\_hcloud) | 1.38.2 |
| <a name="requirement_http"></a> [http](#requirement\_http) | 3.3.0 |

## Providers

| Name | Version |
|------|---------|
| <a name="provider_hcloud"></a> [hcloud](#provider\_hcloud) | 1.38.2 |
| <a name="provider_http"></a> [http](#provider\_http) | 3.3.0 |

## Resources

| Name | Type |
|------|------|
| [hcloud_server.rds](https://registry.terraform.io/providers/hetznercloud/hcloud/1.38.2/docs/resources/server) | resource |
| [hcloud_volume_attachment.backup](https://registry.terraform.io/providers/hetznercloud/hcloud/1.38.2/docs/resources/volume_attachment) | resource |
| [hcloud_volume_attachment.data](https://registry.terraform.io/providers/hetznercloud/hcloud/1.38.2/docs/resources/volume_attachment) | resource |
| [hcloud_volume.backup](https://registry.terraform.io/providers/hetznercloud/hcloud/1.38.2/docs/data-sources/volume) | data source |
| [hcloud_volume.data](https://registry.terraform.io/providers/hetznercloud/hcloud/1.38.2/docs/data-sources/volume) | data source |
| [http_http.cloud_init_bootstrap_solidblocks](https://registry.terraform.io/providers/hashicorp/http/3.3.0/docs/data-sources/http) | data source |

## Inputs

| Name | Description | Type | Default | Required |
|------|-------------|------|---------|:--------:|
| <a name="input_backup_full_calendar"></a> [backup\_full\_calendar](#input\_backup\_full\_calendar) | systemd timer spec for full backups | `string` | `"*-*-* 20:00:00"` | no |
| <a name="input_backup_incr_calendar"></a> [backup\_incr\_calendar](#input\_backup\_incr\_calendar) | systemd timer spec for incremental backups | `string` | `"*-*-* *:00:55"` | no |
| <a name="input_backup_s3_access_key"></a> [backup\_s3\_access\_key](#input\_backup\_s3\_access\_key) | AWS access key for S3 backups. To enable S3 backups `backup_s3_bucket`, `backup_s3_access_key` and `backup_s3_secret_key` have to be provided. | `string` | `null` | no |
| <a name="input_backup_s3_bucket"></a> [backup\_s3\_bucket](#input\_backup\_s3\_bucket) | AWS bucket name for S3 backups. To enable S3 backups `backup_s3_bucket`, `backup_s3_access_key` and `backup_s3_secret_key` have to be provided. | `string` | `null` | no |
| <a name="input_backup_s3_secret_key"></a> [backup\_s3\_secret\_key](#input\_backup\_s3\_secret\_key) | AWS secret key for S3 backups. To enable S3 backups `backup_s3_bucket` `backup_s3_access_key` and `backup_s3_secret_key` have to be provided. | `string` | `null` | no |
| <a name="input_backup_volume"></a> [backup\_volume](#input\_backup\_volume) | backup volume id | `string` | `0` | no |
| <a name="input_data_volume"></a> [data\_volume](#input\_data\_volume) | data volume id | `number` | n/a | yes |
| <a name="input_databases"></a> [databases](#input\_databases) | A list of databases to create when the instance is initialized, for example: `{ id : "database1", user : "user1", password : "password1" }`{ | `list(object({ id : string, user : string, password : string }))` | n/a | yes |
| <a name="input_labels"></a> [labels](#input\_labels) | A list of labels to be attached to the server instance. | `object` | `{}` | no |
| <a name="input_location"></a> [location](#input\_location) | hetzner location | `string` | n/a | yes |
| <a name="input_name"></a> [name](#input\_name) | unique name for the postgres rds instance | `string` | n/a | yes |
| <a name="input_server_type"></a> [server\_type](#input\_server\_type) | hetzner cloud server type | `string` | `"cx11"` | no |
| <a name="input_solidblocks_base_url"></a> [solidblocks\_base\_url](#input\_solidblocks\_base\_url) | override base url for testing purposes | `string` | `"https://github.com"` | no |
| <a name="input_solidblocks_cloud_init_version"></a> [solidblocks\_cloud\_init\_version](#input\_solidblocks\_cloud\_init\_version) | used for integration tests to inject test versions | `string` | `"v0.0.93"` | no |
| <a name="input_solidblocks_version"></a> [solidblocks\_version](#input\_solidblocks\_version) | used for integration tests to inject test versions | `string` | `"v0.0.93"` | no |
| <a name="input_ssh_keys"></a> [ssh\_keys](#input\_ssh\_keys) | ssh keys for instance access | `list(number)` | n/a | yes |

## Outputs

| Name | Description |
|------|-------------|
| <a name="output_ipv4_address"></a> [ipv4\_address](#output\_ipv4\_address) | IpV4 address of the created server |
| <a name="output_this_server_id"></a> [this\_server\_id](#output\_this\_server\_id) | Hetzner ID of the created server |
<!-- END_TF_DOCS -->

## Examples

To prevent accidental data deletion it has proven useful to put the storage devices for the database (data and backup)
into a separate terraform module. This also makes it easier to re-provision the instance without risking deleting
valuable data.

The examples below embrace this pattern, so each example has two different modules:

* `/storage` contains all `hcloud_volume` resources for the databases
* `instance` contains the database instance itself, the storage is looked up using the `hcloud_volume` data lookup

### S3 Backed Backup

Below a minimal example of a PostgreSQL database using S3 as backup storage backend. The full example can be downloaded from the [latest release](https://github.com/pellepelster/solidblocks/releases/latest/).

**instance/main.tf**

```shell
{{% include "/snippets/hetzner-postgres-rds-s3-backup/instance/main.tf" %}}
```

### Local attached storage Backup

Below a minimal example of a PostgreSQL database using a local volume backup storage backend. . The full example can be downloaded from the [latest release](https://github.com/pellepelster/solidblocks/releases/latest/).

**instance/main.tf**

```shell
{{% include "/snippets/hetzner-postgres-rds-local-backup/instance/main.tf" %}}
```