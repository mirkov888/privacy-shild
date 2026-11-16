# Mora Linux Environment (Android)

Mora Linux Environment is an in-app setup guide for running Linux userlands on Android through Termux and proot-distro. It does not bundle a Linux kernel or a virtual machine.

## Supported starter profiles

- Debian (recommended first install)
- Ubuntu
- Arch Linux (proot-distro identifier: `archlinux`)

The available distro names and versions depend on the installed proot-distro release. Check `proot-distro list` in Termux before installing if a profile is unavailable.

## Requirements

1. Install the official Termux app from one trusted source (F-Droid or the official Termux GitHub releases). Do not mix Termux and its plugins from different signing sources.
2. Keep sufficient free internal storage. XFCE and its dependencies can consume several gigabytes.
3. Install a VNC Viewer app separately to display the desktop.
4. Use a current Android version and allow Termux to run in the background if long sessions are needed.

## Setup

The Mora Shield screen **More > Mora Linux Environment** provides copyable commands for the selected distro.

1. In Termux, update packages, install `proot-distro`, and install the selected distro.
2. Enter the distro with `proot-distro login <distro>`.
3. Inside the distro, install XFCE, D-Bus and TigerVNC. Debian/Ubuntu use apt; Arch uses pacman.
4. Create the VNC startup file and start display `:1` bound to localhost only.
5. In VNC Viewer, connect to `127.0.0.1:5901`.

The VNC server is intentionally restricted to loopback. Do not change it to listen on all network interfaces unless you understand the exposure and have configured authentication and firewall rules.

To stop the desktop, run `vncserver -kill :1` inside the distro, then exit the distro shell. A first VNC start may ask you to create a VNC password.

## Architecture and limitations

- Termux provides the Android-side package environment; proot-distro creates a userspace root filesystem and launches it through PRoot.
- The distro shares the Android device kernel. It is not a VM and does not boot its own kernel.
- systemd as PID 1, Docker, kernel modules, privileged mounts, hardware virtualization and full desktop GPU acceleration are not guaranteed and commonly unavailable in PRoot.
- Desktop performance depends on device RAM, storage speed, Android process management and VNC settings. Start with 1280x720 and lower color depth/resolution if the desktop feels slow.
- The Mora Shield screen currently guides setup and copies commands. It does not silently execute shell commands, install Termux, provision a distro, or claim that a desktop session is running.
- Distro backups and removals are performed in Termux with proot-distro commands. Export important files before removing or resetting a distro.

## Security

Commands are shown to the user and copied only after a tap. Mora Shield does not expose a remote shell service, does not open VNC to the LAN, and does not request root access for this integration.
