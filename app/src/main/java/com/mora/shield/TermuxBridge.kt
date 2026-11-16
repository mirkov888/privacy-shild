package com.mora.shield

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build

/**
 * Fixed-command bridge to Termux/proot-distro. Shell input is never taken
 * from arbitrary UI text.
 */
object TermuxBridge {
    const val TERMUX_PACKAGE = "com.termux"
    const val RUN_PERMISSION = "com.termux.permission.RUN_COMMAND"
    private const val ACTION = "com.termux.RUN_COMMAND"
    private const val SERVICE = "com.termux.app.RunCommandService"
    private const val PREFIX = "/data/data/com.termux/files/usr"
    private const val HOME = "/data/data/com.termux/files/home"

    fun installed(context: Context): Boolean = try {
        context.packageManager.getPackageInfo(TERMUX_PACKAGE, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) { false }

    fun hasRunPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < 23 ||
            context.checkSelfPermission(RUN_PERMISSION) == PackageManager.PERMISSION_GRANTED

    fun open(context: Context) {
        val launch = context.packageManager.getLaunchIntentForPackage(TERMUX_PACKAGE)
        if (launch != null) context.startActivity(launch)
    }

    fun installManager(context: Context) {
        run(context, "bootstrap", "$PREFIX/bin/bash",
            arrayOf("-lc", "pkg update -y && pkg install -y proot-distro"), true)
    }

    fun requestStatus(context: Context, distro: String) {
        requireDistro(distro)
        val rootfs = "$PREFIX/var/lib/proot-distro/containers/$distro/rootfs"
        val container = "$PREFIX/var/lib/proot-distro/containers/$distro"
        val script = """
            if [ ! -d "$container" ]; then
                echo MORA_DISTRO_NOT_INSTALLED
                exit 0
            fi
            echo MORA_DISTRO_INSTALLED
            if [ ! -x "$rootfs/bin/sh" ] || [ ! -f "$rootfs/etc/os-release" ]; then
                echo MORA_ROOTFS_BROKEN
                exit 0
            fi
            echo MORA_ROOTFS_OK
            "$PREFIX/bin/proot-distro" login "$distro" -- bash -lc '
                pid="$(vncserver -list 2>/dev/null | awk '\''${'$'}1 == "1" && ${'$'}2 == "5901" { print ${'$'}4; exit }'\'')"
                if [ -n "${'$'}pid" ] && kill -0 "${'$'}pid" 2>/dev/null; then
                    state="$(ps -o stat= -p "${'$'}pid" 2>/dev/null | tr -d " ")"
                    case "${'$'}state" in ""|Z*|z*) echo MORA_XFCE_STOPPED ;; *) echo MORA_XFCE_RUNNING ;; esac
                else
                    echo MORA_XFCE_STOPPED
                fi
            '
        """.trimIndent()
        run(context, "status:$distro", "$PREFIX/bin/bash", arrayOf("-lc", script), true)
    }

    fun installDistro(context: Context, distro: String) {
        requireDistro(distro)
        run(context, "install:$distro", "$PREFIX/bin/bash",
            arrayOf("-lc", installScript(distro, removeFirst = false)), true)
    }

    /** Destructive repair is only called after an explicit UI warning. */
    fun repairDistro(context: Context, distro: String) {
        requireDistro(distro)
        run(context, "repair:$distro", "$PREFIX/bin/bash",
            arrayOf("-lc", installScript(distro, removeFirst = true)), true)
    }

    private fun installScript(distro: String, removeFirst: Boolean): String {
        val remove = if (removeFirst) {
            "\"$PREFIX/bin/proot-distro\" remove $distro >/dev/null 2>&1 || true"
        } else ":"
        return """
            set +e
            $remove
            for attempt in 1 2 3; do
                "$PREFIX/bin/proot-distro" install "$distro" && exit 0
                echo MORA_INSTALL_RETRY
                "$PREFIX/bin/proot-distro" remove "$distro" >/dev/null 2>&1 || true
                sleep 5
            done
            echo MORA_INSTALL_FAILED
            exit 1
        """.trimIndent()
    }

    fun installDesktop(context: Context, distro: String) {
        requireDistro(distro)
        val script = if (distro == "archlinux") {
            "pacman -Syu --noconfirm xfce4 xfce4-goodies tigervnc dbus"
        } else {
            "apt-get update && apt-get install -y xfce4 xfce4-goodies dbus-x11 tigervnc-standalone-server"
        }
        run(context, "desktop:$distro", "$PREFIX/bin/proot-distro",
            arrayOf("login", distro, "--", "bash", "-lc", script), true)
    }

    fun setupVncPassword(context: Context, distro: String) {
        requireDistro(distro)
        run(context, "password:$distro", "$PREFIX/bin/proot-distro",
            arrayOf("login", distro, "--", "vncpasswd"), false)
    }

    fun startDesktop(context: Context, distro: String) {
        requireDistro(distro)
        val script = """
            set -e
            mkdir -p ~/.config/tigervnc ~/.vnc

            cat > ~/.config/tigervnc/xstartup <<'MORA_XSTARTUP'
            #!/bin/sh
            unset SESSION_MANAGER
            unset DBUS_SESSION_BUS_ADDRESS
            exec dbus-run-session startxfce4
            MORA_XSTARTUP
            chmod 700 ~/.config/tigervnc/xstartup
            cp ~/.config/tigervnc/xstartup ~/.vnc/xstartup
            chmod 700 ~/.vnc/xstartup

            pid="${' (vncserver -list 2>/dev/null | awk '$1 == "1" && $2 == "5901" { print $4; exit }')"
            live=false
            if [ -n "@pid" ] && kill -0 "@pid" 2>/dev/null; then
                state="@ (ps -o stat= -p "@pid" 2>/dev/null | tr -d ' ')"
                case "@state" in ""|Z*|z*) ;; *) live=true ;; esac
            fi

            if [ "@live" != true ]; then
                if [ -f /tmp/.X1-lock ]; then
                    oldpid="@ (tr -dc '0-9' < /tmp/.X1-lock)"
                    oldstate="@ (ps -o stat= -p "@oldpid" 2>/dev/null | tr -d ' ')"
                    case "@oldstate" in ""|Z*|z*) rm -f /tmp/.X1-lock /tmp/.X11-unix/X1 ;; esac
                fi
                vncserver :1 -localhost yes -geometry 1280x720 -depth 24
            fi
        """.trimIndent()
        run(context, "start:$distro", "$PREFIX/bin/proot-distro",
            arrayOf("login", "--no-kill-on-exit", distro, "--", "bash", "-lc", script), true)
    }} (vncserver -list 2>/dev/null | awk '$1 == "1" && $2 == "5901" { print $4; exit }')"
            live=false
            if [ -n "${'pid" ] && kill -0 "@pid" 2>/dev/null; then
                state="@ (ps -o stat= -p "@pid" 2>/dev/null | tr -d ' ')"
                case "@state" in ""|Z*|z*) ;; *) live=true ;; esac
            fi

            if [ "@live" != true ]; then
                if [ -f /tmp/.X1-lock ]; then
                    oldpid="@ (tr -dc '0-9' < /tmp/.X1-lock)"
                    oldstate="@ (ps -o stat= -p "@oldpid" 2>/dev/null | tr -d ' ')"
                    case "@oldstate" in ""|Z*|z*) rm -f /tmp/.X1-lock /tmp/.X11-unix/X1 ;; esac
                fi
                vncserver :1 -localhost yes -geometry 1280x720 -depth 24
            fi
        """.trimIndent()
        run(context, "start:$distro", "$PREFIX/bin/proot-distro",
            arrayOf("login", "--no-kill-on-exit", distro, "--", "bash", "-lc", script), true)
    }}pid" ] && kill -0 "${'pid" 2>/dev/null; then
                state="@ (ps -o stat= -p "@pid" 2>/dev/null | tr -d ' ')"
                case "@state" in ""|Z*|z*) ;; *) live=true ;; esac
            fi

            if [ "@live" != true ]; then
                if [ -f /tmp/.X1-lock ]; then
                    oldpid="@ (tr -dc '0-9' < /tmp/.X1-lock)"
                    oldstate="@ (ps -o stat= -p "@oldpid" 2>/dev/null | tr -d ' ')"
                    case "@oldstate" in ""|Z*|z*) rm -f /tmp/.X1-lock /tmp/.X11-unix/X1 ;; esac
                fi
                vncserver :1 -localhost yes -geometry 1280x720 -depth 24
            fi
        """.trimIndent()
        run(context, "start:$distro", "$PREFIX/bin/proot-distro",
            arrayOf("login", "--no-kill-on-exit", distro, "--", "bash", "-lc", script), true)
    }}pid" 2>/dev/null; then
                state="${' (ps -o stat= -p "@pid" 2>/dev/null | tr -d ' ')"
                case "@state" in ""|Z*|z*) ;; *) live=true ;; esac
            fi

            if [ "@live" != true ]; then
                if [ -f /tmp/.X1-lock ]; then
                    oldpid="@ (tr -dc '0-9' < /tmp/.X1-lock)"
                    oldstate="@ (ps -o stat= -p "@oldpid" 2>/dev/null | tr -d ' ')"
                    case "@oldstate" in ""|Z*|z*) rm -f /tmp/.X1-lock /tmp/.X11-unix/X1 ;; esac
                fi
                vncserver :1 -localhost yes -geometry 1280x720 -depth 24
            fi
        """.trimIndent()
        run(context, "start:$distro", "$PREFIX/bin/proot-distro",
            arrayOf("login", "--no-kill-on-exit", distro, "--", "bash", "-lc", script), true)
    }} (ps -o stat= -p "${'pid" 2>/dev/null | tr -d ' ')"
                case "@state" in ""|Z*|z*) ;; *) live=true ;; esac
            fi

            if [ "@live" != true ]; then
                if [ -f /tmp/.X1-lock ]; then
                    oldpid="@ (tr -dc '0-9' < /tmp/.X1-lock)"
                    oldstate="@ (ps -o stat= -p "@oldpid" 2>/dev/null | tr -d ' ')"
                    case "@oldstate" in ""|Z*|z*) rm -f /tmp/.X1-lock /tmp/.X11-unix/X1 ;; esac
                fi
                vncserver :1 -localhost yes -geometry 1280x720 -depth 24
            fi
        """.trimIndent()
        run(context, "start:$distro", "$PREFIX/bin/proot-distro",
            arrayOf("login", "--no-kill-on-exit", distro, "--", "bash", "-lc", script), true)
    }}pid" 2>/dev/null | tr -d ' ')"
                case "${'state" in ""|Z*|z*) ;; *) live=true ;; esac
            fi

            if [ "@live" != true ]; then
                if [ -f /tmp/.X1-lock ]; then
                    oldpid="@ (tr -dc '0-9' < /tmp/.X1-lock)"
                    oldstate="@ (ps -o stat= -p "@oldpid" 2>/dev/null | tr -d ' ')"
                    case "@oldstate" in ""|Z*|z*) rm -f /tmp/.X1-lock /tmp/.X11-unix/X1 ;; esac
                fi
                vncserver :1 -localhost yes -geometry 1280x720 -depth 24
            fi
        """.trimIndent()
        run(context, "start:$distro", "$PREFIX/bin/proot-distro",
            arrayOf("login", "--no-kill-on-exit", distro, "--", "bash", "-lc", script), true)
    }}state" in ""|Z*|z*) ;; *) live=true ;; esac
            fi

            if [ "${'live" != true ]; then
                if [ -f /tmp/.X1-lock ]; then
                    oldpid="@ (tr -dc '0-9' < /tmp/.X1-lock)"
                    oldstate="@ (ps -o stat= -p "@oldpid" 2>/dev/null | tr -d ' ')"
                    case "@oldstate" in ""|Z*|z*) rm -f /tmp/.X1-lock /tmp/.X11-unix/X1 ;; esac
                fi
                vncserver :1 -localhost yes -geometry 1280x720 -depth 24
            fi
        """.trimIndent()
        run(context, "start:$distro", "$PREFIX/bin/proot-distro",
            arrayOf("login", "--no-kill-on-exit", distro, "--", "bash", "-lc", script), true)
    }}live" != true ]; then
                if [ -f /tmp/.X1-lock ]; then
                    oldpid="${' (tr -dc '0-9' < /tmp/.X1-lock)"
                    oldstate="@ (ps -o stat= -p "@oldpid" 2>/dev/null | tr -d ' ')"
                    case "@oldstate" in ""|Z*|z*) rm -f /tmp/.X1-lock /tmp/.X11-unix/X1 ;; esac
                fi
                vncserver :1 -localhost yes -geometry 1280x720 -depth 24
            fi
        """.trimIndent()
        run(context, "start:$distro", "$PREFIX/bin/proot-distro",
            arrayOf("login", "--no-kill-on-exit", distro, "--", "bash", "-lc", script), true)
    }} (tr -dc '0-9' < /tmp/.X1-lock)"
                    oldstate="${' (ps -o stat= -p "@oldpid" 2>/dev/null | tr -d ' ')"
                    case "@oldstate" in ""|Z*|z*) rm -f /tmp/.X1-lock /tmp/.X11-unix/X1 ;; esac
                fi
                vncserver :1 -localhost yes -geometry 1280x720 -depth 24
            fi
        """.trimIndent()
        run(context, "start:$distro", "$PREFIX/bin/proot-distro",
            arrayOf("login", "--no-kill-on-exit", distro, "--", "bash", "-lc", script), true)
    }} (ps -o stat= -p "${'oldpid" 2>/dev/null | tr -d ' ')"
                    case "@oldstate" in ""|Z*|z*) rm -f /tmp/.X1-lock /tmp/.X11-unix/X1 ;; esac
                fi
                vncserver :1 -localhost yes -geometry 1280x720 -depth 24
            fi
        """.trimIndent()
        run(context, "start:$distro", "$PREFIX/bin/proot-distro",
            arrayOf("login", "--no-kill-on-exit", distro, "--", "bash", "-lc", script), true)
    }}oldpid" 2>/dev/null | tr -d ' ')"
                    case "${'oldstate" in ""|Z*|z*) rm -f /tmp/.X1-lock /tmp/.X11-unix/X1 ;; esac
                fi
                vncserver :1 -localhost yes -geometry 1280x720 -depth 24
            fi
        """.trimIndent()
        run(context, "start:$distro", "$PREFIX/bin/proot-distro",
            arrayOf("login", "--no-kill-on-exit", distro, "--", "bash", "-lc", script), true)
    }}oldstate" in ""|Z*|z*) rm -f /tmp/.X1-lock /tmp/.X11-unix/X1 ;; esac
                fi
                vncserver :1 -localhost yes -geometry 1280x720 -depth 24
            fi
        """.trimIndent()
        run(context, "start:$distro", "$PREFIX/bin/proot-distro",
            arrayOf("login", "--no-kill-on-exit", distro, "--", "bash", "-lc", script), true)
    }
    fun stopDesktop(context: Context, distro: String) {
        requireDistro(distro)
        run(context, "stop:$distro", "$PREFIX/bin/proot-distro",
            arrayOf("login", distro, "--", "bash", "-lc", "vncserver -kill :1 || true"), true)
    }

    private fun requireDistro(distro: String) {
        require(distro in setOf("debian", "ubuntu", "archlinux")) { "Unsupported distro" }
    }

    private fun run(context: Context, operation: String, path: String, args: Array<String>, background: Boolean) {
        check(installed(context)) { "Termux belum terpasang" }
        check(hasRunPermission(context)) { "Izin Run commands in Termux belum diberikan" }
        val callback = Intent(context, TermuxResultReceiver::class.java).apply {
            action = "com.mora.shield.TERMUX_RESULT"
            putExtra(TermuxResultReceiver.EXTRA_OPERATION, operation)
        }
        val flags = PendingIntent.FLAG_ONE_SHOT or
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
        val pending = PendingIntent.getBroadcast(context, operation.hashCode(), callback, flags)
        val intent = Intent(ACTION).apply {
            setClassName(TERMUX_PACKAGE, SERVICE)
            putExtra("com.termux.RUN_COMMAND_PATH", path)
            putExtra("com.termux.RUN_COMMAND_ARGUMENTS", args)
            putExtra("com.termux.RUN_COMMAND_WORKDIR", HOME)
            putExtra("com.termux.RUN_COMMAND_BACKGROUND", background)
            putExtra("com.termux.RUN_COMMAND_PENDING_INTENT", pending)
        }
        context.startService(intent)
    }
}
