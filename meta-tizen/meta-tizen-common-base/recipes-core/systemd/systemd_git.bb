SUMMARY = "System and service manager for Linux, replacing SysVinit"
HOMEPAGE = "http://www.freedesktop.org/wiki/Software/systemd"

LICENSE = "GPLv2 & LGPLv2.1 & MIT"
LIC_FILES_CHKSUM = "file://LICENSE.GPL2;md5=751419260aa954499f7abaabaa882bbe \
                    file://LICENSE.LGPL2.1;md5=4fbd65380cdd255951079008b364516c \
                    file://LICENSE.MIT;md5=544799d0b492f119fa04641d1b8868ed"

PROVIDES = "udev"

PE = "1"

DEPENDS = "kmod docbook-sgml-dtd-4.1-native intltool-native gperf-native acl readline dbus libcap libcgroup glib-2.0 qemu-native util-linux"
DEPENDS += "${@bb.utils.contains('DISTRO_FEATURES', 'pam', 'libpam', '', d)}"

SECTION = "base/shell"

inherit gtk-doc useradd pkgconfig autotools perlnative update-rc.d update-alternatives qemu systemd ptest gettext

SRCREV = "1c6d7a3b259467df3c02c1fb0bb4c57d70192d15"

PV = "216+git${SRCPV}"

SRC_URI = "git://review.tizen.org/platform/upstream/systemd;protocol=git;tag=1c6d7a3b259467df3c02c1fb0bb4c57d70192d15;nobranch=1 \
           file://touchscreen.rules \
           file://00-create-volatile.conf \
           file://init \
           file://run-ptest \
           file://missing-mips.patch \
           "

S = "${WORKDIR}/git"

SRC_URI_append_libc-uclibc = "\
                             file://systemd-pam-fix-getty-unit.patch \
                            "
LDFLAGS_append_libc-uclibc = " -lrt"

GTKDOC_DOCDIR = "${S}/docs/"

PACKAGECONFIG ??= "xz"
PACKAGECONFIG[journal-upload] = "--enable-libcurl,--disable-libcurl,curl"
# Sign the journal for anti-tampering
PACKAGECONFIG[gcrypt] = "--enable-gcrypt,--disable-gcrypt,libgcrypt"
# regardless of PACKAGECONFIG, libgcrypt is always required to expand
# the AM_PATH_LIBGCRYPT autoconf macro
DEPENDS += "libgcrypt"
# Compress the journal
PACKAGECONFIG[xz] = "--enable-xz,--disable-xz,xz"
PACKAGECONFIG[cryptsetup] = "--enable-libcryptsetup,--disable-libcryptsetup,cryptsetup"
PACKAGECONFIG[microhttpd] = "--enable-microhttpd,--disable-microhttpd,libmicrohttpd"
PACKAGECONFIG[elfutils] = "--enable-elfutils,--disable-elfutils,elfutils"
PACKAGECONFIG[resolved] = "--enable-resolved,--disable-resolved"
PACKAGECONFIG[networkd] = "--enable-networkd,--disable-networkd"

CACHED_CONFIGUREVARS = "ac_cv_path_KILL=${base_bindir}/kill"

# Helper variables to clarify locations.  This mirrors the logic in systemd's
# build system.
rootprefix ?= "${base_prefix}"
rootlibdir ?= "${base_libdir}"
rootlibexecdir = "${rootprefix}/lib"

# The gtk+ tools should get built as a separate recipe e.g. systemd-tools
EXTRA_OECONF = " --with-rootprefix=${rootprefix} \
                 --with-rootlibdir=${rootlibdir} \
                 --with-roothomedir=${ROOT_HOME} \
                 ${@bb.utils.contains('DISTRO_FEATURES', 'pam', '--enable-pam', '--disable-pam', d)} \
                 --disable-manpages \
                 --disable-coredump \
                 --disable-introspection \
                 --disable-kdbus \
                 --enable-split-usr \
                 --without-python \
                 --with-sysvrcnd-path= \
                 --with-firmware-path=/lib/firmware \
                 --enable-compat-libs \
                 ac_cv_path_KILL=${base_bindir}/kill \
               "

EXTRA_OECONF += " --enable-bootchart --disable-sysusers --disable-firstboot --disable-timesyncd --disable-resolved --disable-networkd --libexecdir=${prefix}/lib --docdir=${prefix}/share/doc/packages/systemd --disable-static --disable-libcurl --with-sysvinit-path=  --with-smack-run-label=System cc_cv_CFLAGS__flto=no"



# uclibc does not have NSS
EXTRA_OECONF_append_libc-uclibc = " --disable-myhostname "

do_configure_prepend() {
	export CPP="${HOST_PREFIX}cpp ${TOOLCHAIN_OPTIONS} ${HOST_CC_ARCH}"
	export NM="${HOST_PREFIX}gcc-nm"
	export AR="${HOST_PREFIX}gcc-ar"
	export RANLIB="${HOST_PREFIX}gcc-ranlib"
	export KMOD="${base_bindir}/kmod"
	if [ -d ${S}/units.pre_sed ] ; then
		cp -r ${S}/units.pre_sed ${S}/units
	else
		cp -r ${S}/units ${S}/units.pre_sed
	fi
	sed -i '/ln --relative --help/d' ${S}/configure.ac
	sed -i -e 's:\$(LN_S) --relative -f:lnr:g' ${S}/Makefile.am
	sed -i -e 's:\$(LN_S) --relative:lnr:g' ${S}/Makefile.am
}

do_install() {
	autotools_do_install
	install -d ${D}/${base_sbindir}
	# Provided by a separate recipe
	rm ${D}${systemd_unitdir}/system/serial-getty* -f

	# Provide support for initramfs
	[ ! -e ${D}/init ] && ln -s ${rootlibexecdir}/systemd/systemd ${D}/init
	[ ! -e ${D}/${base_sbindir}/udevd ] && ln -s ${rootlibexecdir}/systemd/systemd-udevd ${D}/${base_sbindir}/udevd

	# Create machine-id
	# 20:12 < mezcalero> koen: you have three options: a) run systemd-machine-id-setup at install time, b) have / read-only and an empty file there (for stateless) and c) boot with / writable
	touch ${D}${sysconfdir}/machine-id

	install -m 0644 ${WORKDIR}/*.rules ${D}${sysconfdir}/udev/rules.d/

	install -m 0644 ${WORKDIR}/00-create-volatile.conf ${D}${sysconfdir}/tmpfiles.d/

	if ${@bb.utils.contains('DISTRO_FEATURES','sysvinit','true','false',d)}; then
		install -d ${D}${sysconfdir}/init.d
		install -m 0755 ${WORKDIR}/init ${D}${sysconfdir}/init.d/systemd-udevd
		sed -i s%@UDEVD@%${rootlibexecdir}/systemd/systemd-udevd% ${D}${sysconfdir}/init.d/systemd-udevd
	fi

	# Move libgudev back to ${rootlibdir} to keep backward compatibility
	[ ${rootlibdir} != ${libdir} ] && mv -t ${D}${rootlibdir} ${D}${libdir}/libgudev*

        # Delete journal README, as log can be symlinked inside volatile.
        rm -f ${D}/${localstatedir}/log/README
        rm -rf ${D}/${localstatedir}/log


	# Create symlinks for systemd-update-utmp-runlevel.service
	install -d ${D}${systemd_unitdir}/system/graphical.target.wants
	install -d ${D}${systemd_unitdir}/system/multi-user.target.wants
	install -d ${D}${systemd_unitdir}/system/poweroff.target.wants
	install -d ${D}${systemd_unitdir}/system/reboot.target.wants
	install -d ${D}${systemd_unitdir}/system/rescue.target.wants
	ln -sf ../systemd-update-utmp-runlevel.service ${D}${systemd_unitdir}/system/graphical.target.wants/systemd-update-utmp-runlevel.service
	ln -sf ../systemd-update-utmp-runlevel.service ${D}${systemd_unitdir}/system/multi-user.target.wants/systemd-update-utmp-runlevel.service
	ln -sf ../systemd-update-utmp-runlevel.service ${D}${systemd_unitdir}/system/poweroff.target.wants/systemd-update-utmp-runlevel.service
	ln -sf ../systemd-update-utmp-runlevel.service ${D}${systemd_unitdir}/system/reboot.target.wants/systemd-update-utmp-runlevel.service
	ln -sf ../systemd-update-utmp-runlevel.service ${D}${systemd_unitdir}/system/rescue.target.wants/systemd-update-utmp-runlevel.service

	# Enable journal to forward message to syslog daemon
	sed -i -e 's/.*ForwardToSyslog.*/ForwardToSyslog=yes/' ${D}${sysconfdir}/systemd/journald.conf
	### TIZEN PART SPECIFIC #####
	# legacy links
	ln -sf loginctl ${D}${prefix}/bin/systemd-loginctl
	
	# We create all wants links manually at installation time to make sure
	# they are not owned and hence overriden by rpm after the used deleted
	# them.
	rm -rf ${D}${sysconfdir}/systemd/system/*.target.wants
	
	# Make sure the ghost-ing below works
	touch ${D}${sysconfdir}/systemd/system/runlevel2.target
	touch ${D}${sysconfdir}/systemd/system/runlevel3.target
	touch ${D}${sysconfdir}/systemd/system/runlevel4.target
	touch ${D}${sysconfdir}/systemd/system/runlevel5.target
	
	# Make sure these directories are properly owned
	mkdir -p ${D}${systemd_unitdir}/system/basic.target.wants
	mkdir -p ${D}${systemd_unitdir}/system/default.target.wants
	mkdir -p ${D}${systemd_unitdir}/system/dbus.target.wants
	mkdir -p ${D}${systemd_unitdir}/system/syslog.target.wants
	
	# Make sure the user generators dir exists too
	mkdir -p ${D}/lib/systemd/system-generators
	mkdir -p ${D}${prefix}/lib/systemd/user-generators
	
	# Create new-style configuration files so that we can ghost-own them
	touch ${D}${sysconfdir}/hostname
	touch ${D}${sysconfdir}/vconsole.conf
	touch ${D}${sysconfdir}/locale.conf
	touch ${D}${sysconfdir}/machine-id
	touch ${D}${sysconfdir}/machine-info
	touch ${D}${sysconfdir}/timezone
	
	mkdir -p ${D}/lib/systemd/system-preset/
	mkdir -p ${D}/lib/systemd/user-preset/
	
	# Make sure the shutdown/sleep drop-in dirs exist
	mkdir -p ${D}/lib/systemd/system-shutdown/
	mkdir -p ${D}/lib/systemd/system-sleep/
	
	# Make sure the NTP units dir exists
	mkdir -p ${D}${prefix}/lib/systemd/ntp-units.d/
	
	# Install modprobe fragment
	mkdir -p ${D}${sysconfdir}/modprobe.d/
	
	# Fix the dangling /var/lock -> /run/lock symlink
	install -Dm644 ${S}/tmpfiles.d/legacy.conf ${D}${prefix}/lib/tmpfiles.d/legacy.conf
	
	install -m644 ${S}/packaging/pamconsole-tmp.conf ${D}${prefix}/lib/tmpfiles.d/
	
	rm -rf ${D}${systemd_unitdir}/system/default.target
	install -m 755 -d ${D}${systemd_unitdir}/system
	install -m 644 ${S}/packaging/default.target ${D}${systemd_unitdir}/system/
	
	rm -rf ${D}${prefix}/share/doc/packages/systemd
	
	# Disable some useless services in Tizen
	rm -rf ${D}${prefix}/lib/systemd/user/sysinit.target.wants/dev-hugepages.mount
	rm -rf ${D}${prefix}/lib/systemd/user/sysinit.target.wants/sys-fs-fuse-connections.mount
	rm -rf ${D}${prefix}/lib/systemd/user/sysinit.target.wants/systemd-binfmt.service
	rm -rf ${D}${prefix}/lib/systemd/user/sysinit.target.wants/systemd-modules-load.service
	rm -rf ${D}${prefix}/lib/systemd/user/sysinit.target.wants/systemd-ask-password-console.path
	rm -rf ${D}${prefix}/lib/systemd/user/multi-user.target.wants/systemd-ask-password-wall.path
	
	# Move macros to the proper location for Tizen
	mkdir -p ${D}${sysconfdir}/rpm
	install -m644 ${B}/src/core/macros.systemd ${D}${sysconfdir}/rpm/macros.systemd
	
	rm -fr ${D}${prefix}/lib/rpm
	rm -fr ${D}${sysconfdir}/kernel
	rm -fr ${D}${sysconfdir}/modprobe.d
	rm -fr ${D}${localstatedir}
	
	# Exclude ELF binaries
	rm -f ${D}/lib/systemd/system-generators/systemd-debug-generator
	rm -f ${D}${prefix}/lib/systemd/system-generators/systemd-hibernate-resume-generator
 



}

do_install_ptest () {
       install -d ${D}${PTEST_PATH}/test
       cp -rf ${S}/test/* ${D}${PTEST_PATH}/test
       install -m 0755  ${B}/test-udev ${D}${PTEST_PATH}/
       install -d ${D}${PTEST_PATH}/build-aux
       cp ${S}/build-aux/test-driver ${D}${PTEST_PATH}/build-aux/
       cp -rf ${B}/rules ${D}${PTEST_PATH}/
       # This directory needs to be there for udev-test.pl to work.
       install -d ${D}${libdir}/udev/rules.d
       cp ${B}/Makefile ${D}${PTEST_PATH}/
       cp ${S}/test/sys.tar.xz ${D}${PTEST_PATH}/test
       sed -i 's/"tree"/"ls"/' ${D}${PTEST_PATH}/test/udev-test.pl
       sed -i 's#${S}#${PTEST_PATH}#g' ${D}${PTEST_PATH}/Makefile
       sed -i 's#${B}#${PTEST_PATH}#g' ${D}${PTEST_PATH}/Makefile
}

python populate_packages_prepend (){
    systemdlibdir = d.getVar("rootlibdir", True)
    do_split_packages(d, systemdlibdir, '^lib(.*)\.so\.*', 'lib%s', 'Systemd %s library', extra_depends='', allow_links=True)
}
PACKAGES_DYNAMIC += "^lib(udev|gudev|systemd).*"

PACKAGES =+ "${PN}-gui ${PN}-vconsole-setup ${PN}-initramfs ${PN}-analyze ${PN}-kernel-install \
             ${PN}-rpm-macros ${PN}-binfmt ${PN}-pam ${PN}-zsh"

SYSTEMD_PACKAGES = "${PN}-binfmt"
SYSTEMD_SERVICE_${PN}-binfmt = "systemd-binfmt.service"

USERADD_PACKAGES = "${PN}"
USERADD_PARAM_${PN} += "--system systemd-journal-gateway"
GROUPADD_PARAM_${PN} = "-r lock; -r systemd-journal"

FILES_${PN}-analyze = "${bindir}/systemd-analyze"

FILES_${PN}-initramfs = "/init"
RDEPENDS_${PN}-initramfs = "${PN}"

# The test cases need perl and bash to run correctly.
RDEPENDS_${PN}-ptest += "perl bash"
FILES_${PN}-ptest += "${libdir}/udev/rules.d"

FILES_${PN}-dbg += "${libdir}/systemd/ptest/.debug"

FILES_${PN}-gui = "${bindir}/systemadm"

FILES_${PN}-vconsole-setup = "${rootlibexecdir}/systemd/systemd-vconsole-setup \
                              ${systemd_unitdir}/system/systemd-vconsole-setup.service \
                              ${systemd_unitdir}/system/sysinit.target.wants/systemd-vconsole-setup.service"

FILES_${PN}-kernel-install = "${bindir}/kernel-install \
                              ${sysconfdir}/kernel/ \
                              ${exec_prefix}/lib/kernel \
                             "
FILES_${PN}-rpm-macros = "${exec_prefix}/lib/rpm \
                         "

FILES_${PN}-zsh = "${datadir}/zsh/site-functions"

FILES_${PN}-binfmt = "${sysconfdir}/binfmt.d/ \
                      ${exec_prefix}/lib/binfmt.d \
                      ${rootlibexecdir}/systemd/systemd-binfmt \
                      ${systemd_unitdir}/system/proc-sys-fs-binfmt_misc.* \
                      ${systemd_unitdir}/system/systemd-binfmt.service"
RRECOMMENDS_${PN}-binfmt = "kernel-module-binfmt-misc"

RRECOMMENDS_${PN}-vconsole-setup = "kbd kbd-consolefonts kbd-keymaps"

CONFFILES_${PN} = "${sysconfdir}/systemd/journald.conf \
                ${sysconfdir}/systemd/logind.conf \
                ${sysconfdir}/systemd/system.conf \
                ${sysconfdir}/systemd/user.conf"

FILES_${PN} = " ${base_bindir}/* \
                ${datadir}/bash-completion \
                ${datadir}/dbus-1/services \
                ${datadir}/dbus-1/system-services \
                ${datadir}/polkit-1 \
                ${datadir}/${BPN} \
                ${datadir}/factory \
                ${sysconfdir}/bash_completion.d/ \
                ${sysconfdir}/dbus-1/ \
                ${sysconfdir}/machine-id \
                ${sysconfdir}/modules-load.d/ \
                ${sysconfdir}/sysctl.d/ \
                ${sysconfdir}/systemd/ \
                ${sysconfdir}/tmpfiles.d/ \
                ${sysconfdir}/xdg/ \
                ${sysconfdir}/init.d/README \
                ${rootlibexecdir}/systemd/* \
                ${systemd_unitdir}/* \
                ${base_libdir}/security/*.so \
                ${libdir}/libnss_* \
                /cgroup \
                ${bindir}/systemd* \
                ${bindir}/busctl \
                ${bindir}/localectl \
                ${bindir}/hostnamectl \
                ${bindir}/timedatectl \
                ${bindir}/bootctl \
                ${bindir}/kernel-install \
                ${exec_prefix}/lib/tmpfiles.d/*.conf \
                ${exec_prefix}/lib/systemd \
                ${exec_prefix}/lib/modules-load.d \
                ${exec_prefix}/lib/sysctl.d \
                ${exec_prefix}/lib/sysusers.d \
                ${localstatedir} \
                /lib/udev/rules.d/70-uaccess.rules \
                /lib/udev/rules.d/71-seat.rules \
                /lib/udev/rules.d/73-seat-late.rules \
                /lib/udev/rules.d/99-systemd.rules \
                ${@bb.utils.contains('DISTRO_FEATURES', 'pam', '${sysconfdir}/pam.d', '', d)} \
               "

FILES_${PN}-dbg += "${rootlibdir}/.debug ${systemd_unitdir}/.debug ${systemd_unitdir}/*/.debug ${base_libdir}/security/.debug/"
FILES_${PN}-dev += "${base_libdir}/security/*.la ${datadir}/dbus-1/interfaces/ ${sysconfdir}/rpm/macros.systemd"

RDEPENDS_${PN} += "kmod dbus util-linux-mount udev (= ${EXTENDPKGV})"
RDEPENDS_${PN} += "volatile-binds"

RRECOMMENDS_${PN} += "systemd-serialgetty systemd-compat-units udev-hwdb\
                      util-linux-agetty \
                      util-linux-fsck e2fsprogs-e2fsck \
                      kernel-module-autofs4 kernel-module-unix kernel-module-ipv6 os-release \
"

PACKAGES =+ "udev-dbg udev udev-hwdb"

FILES_udev-dbg += "/lib/udev/.debug"

RPROVIDES_udev = "hotplug"

RDEPENDS_udev-hwdb += "udev"

FILES_udev += "${base_sbindir}/udevd \
               ${rootlibexecdir}/systemd/systemd-udevd \
               ${rootlibexecdir}/udev/accelerometer \
               ${rootlibexecdir}/udev/ata_id \
               ${rootlibexecdir}/udev/cdrom_id \
               ${rootlibexecdir}/udev/collect \
               ${rootlibexecdir}/udev/findkeyboards \
               ${rootlibexecdir}/udev/keyboard-force-release.sh \
               ${rootlibexecdir}/udev/keymap \
               ${rootlibexecdir}/udev/mtd_probe \
               ${rootlibexecdir}/udev/scsi_id \
               ${rootlibexecdir}/udev/v4l_id \
               ${rootlibexecdir}/udev/keymaps \
               ${rootlibexecdir}/udev/rules.d/4*.rules \
               ${rootlibexecdir}/udev/rules.d/5*.rules \
               ${rootlibexecdir}/udev/rules.d/6*.rules \
               ${rootlibexecdir}/udev/rules.d/70-power-switch.rules \
               ${rootlibexecdir}/udev/rules.d/75*.rules \
               ${rootlibexecdir}/udev/rules.d/78*.rules \
               ${rootlibexecdir}/udev/rules.d/8*.rules \
               ${rootlibexecdir}/udev/rules.d/95*.rules \
               ${sysconfdir}/udev \
               ${sysconfdir}/init.d/systemd-udevd \
               ${systemd_unitdir}/system/*udev* \
               ${systemd_unitdir}/system/*.wants/*udev* \
               ${base_bindir}/udevadm \
               ${datadir}/bash-completion/completions/udevadm \
              "

FILES_udev-hwdb = "${rootlibexecdir}/udev/hwdb.d"

INITSCRIPT_PACKAGES = "udev"
INITSCRIPT_NAME_udev = "systemd-udevd"
INITSCRIPT_PARAMS_udev = "start 03 S ."

python __anonymous() {
    if not bb.utils.contains('DISTRO_FEATURES', 'sysvinit', True, False, d):
        d.setVar("INHIBIT_UPDATERCD_BBCLASS", "1")
}

# TODO:
# u-a for runlevel and telinit

ALTERNATIVE_${PN} = "init halt reboot shutdown poweroff runlevel"

ALTERNATIVE_TARGET[init] = "${rootlibexecdir}/systemd/systemd"
ALTERNATIVE_LINK_NAME[init] = "${base_sbindir}/init"
ALTERNATIVE_PRIORITY[init] ?= "300"

ALTERNATIVE_TARGET[halt] = "${base_bindir}/systemctl"
ALTERNATIVE_LINK_NAME[halt] = "${base_sbindir}/halt"
ALTERNATIVE_PRIORITY[halt] ?= "300"

ALTERNATIVE_TARGET[reboot] = "${base_bindir}/systemctl"
ALTERNATIVE_LINK_NAME[reboot] = "${base_sbindir}/reboot"
ALTERNATIVE_PRIORITY[reboot] ?= "300"

ALTERNATIVE_TARGET[shutdown] = "${base_bindir}/systemctl"
ALTERNATIVE_LINK_NAME[shutdown] = "${base_sbindir}/shutdown"
ALTERNATIVE_PRIORITY[shutdown] ?= "300"

ALTERNATIVE_TARGET[poweroff] = "${base_bindir}/systemctl"
ALTERNATIVE_LINK_NAME[poweroff] = "${base_sbindir}/poweroff"
ALTERNATIVE_PRIORITY[poweroff] ?= "300"

ALTERNATIVE_TARGET[runlevel] = "${base_bindir}/systemctl"
ALTERNATIVE_LINK_NAME[runlevel] = "${base_sbindir}/runlevel"
ALTERNATIVE_PRIORITY[runlevel] ?= "300"

pkg_postinst_udev-hwdb () {
	if test -n "$D"; then
		${@qemu_run_binary(d, '$D', '${base_bindir}/udevadm')} hwdb --update \
			--root $D
	else
		udevadm hwdb --update
	fi
}

pkg_prerm_udev-hwdb () {
	if test -n "$D"; then
		exit 1
	fi

	rm -f ${sysconfdir}/udev/hwdb.bin
}

# As this recipe builds udev, respect systemd being in DISTRO_FEATURES so
# that we don't build both udev and systemd in world builds.
python () {
    if not bb.utils.contains ('DISTRO_FEATURES', 'systemd', True, False, d):
        raise bb.parse.SkipPackage("'systemd' not in DISTRO_FEATURES")
}
