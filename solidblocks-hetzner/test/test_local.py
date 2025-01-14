import time


def test_storge_mounts(host):
    assert host.mount_point(f"/storage/data").exists
    assert host.mount_point(f"/storage/backup").exists


def test_unattended_upgrade_enabled(host):
    assert host.file(f"/etc/apt/apt.conf.d/50unattended-upgrades").exists
    assert host.service(f"unattended-upgrades").is_enabled
    assert host.service(f"unattended-upgrades").is_running


def test_user_data(host):
    """ ensure script from user data supplied to the module was executed """

    assert host.package("telnet").is_installed
