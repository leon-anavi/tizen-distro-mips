DESCRIPTION = "DB library for calendar"
HOMEPAGE = "http://nohomepage.org"
SECTION = "Social & Content/Calendar"
LICENSE = "Apache 2.0"

SRC_URI = ""

S = "${WORKDIR}/git"

inherit autotools-brokensep

BBCLASSEXTEND = ""
PROVIDES = ""

#PROVIDES by calendar-service


#PROVIDES by calendar-service-dev
PROVIDES += "calendar-service-dev"


RDEPENDS = ""
#RDEPENDS of calendar-service (${PN})
RDEPENDS_${PN} += "sqlite3"
RDEPENDS_${PN} += "coreutils"
RDEPENDS_${PN} += "eglibc"

#RDEPENDS of calendar-service-dev (${PN}-dev)
RDEPENDS_${PN}-dev += "calendar-service"
RDEPENDS_${PN}-dev += "alarm-manager"


DEPENDS = ""
#DEPENDS of calendar-service
DEPENDS += "pims-ipc"
DEPENDS_append_class-native = " cmake-native"
DEPENDS_append_class-target = " cmake-native"
DEPENDS += "contacts-service"
DEPENDS += "bundle"
DEPENDS += "tizen-platform-config"
DEPENDS += "app-svc"
DEPENDS += "dlog"
DEPENDS += "glib-2.0"
DEPENDS += "alarm-manager"
DEPENDS += "icu"
DEPENDS += "sqlite3"
DEPENDS += "vconf"
DEPENDS += "libslp-db-util"
DEPENDS += "common"

do_prep() {
 cd ${S}
 chmod -Rf a+rX,u+w,g-w,o-w ${S}
 #setup -q
 cp ${S}/packaging/calendar-service.manifest .
 
 
 
}
do_patch_append() {
    bb.build.exec_func('do_prep', d)
}

do_configure() {
}

do_compile() {
 cd ${S}
 LANG=C
 export LANG
 unset DISPLAY
 LD_AS_NEEDED=1; export LD_AS_NEEDED ;
 
 
   cmake \
         -DCMAKE_VERBOSE_MAKEFILE=ON \
         -DCMAKE_INSTALL_PREFIX:PATH=${prefix} \
         -DCMAKE_INSTALL_LIBDIR:PATH=${prefix}/lib \
         -DINCLUDE_INSTALL_DIR:PATH=${prefix}/include \
         -DLIB_INSTALL_DIR:PATH=${prefix}/lib \
         -DSYSCONF_INSTALL_DIR:PATH=${sysconfdir} \
         -DSHARE_INSTALL_PREFIX:PATH=${prefix}/share \
         -DCMAKE_SKIP_RPATH:BOOL=ON \
         -DBUILD_SHARED_LIBS:BOOL=ON \
         ${EXTRA_OECMAKE} .
 
 
 make -j16
 
 
 
}

do_install() {
 export RPM_BUILD_ROOT=${D}
 cd ${S}
 LANG=C
 export LANG
 unset DISPLAY
 rm -rf ${D}
 mkdir -p ${D}
 
 
   oe_runmake \
         DESTDIR=${D} \
         INSTALL_ROOT=${D} \
         BINDIR=${prefix}/bin \
   install  
   rm -f ${D}${infodir}/dir 
   find ${D} -regex ".*\.la$" | xargs rm -f -- 
   find ${D} -regex ".*\.a$" | xargs rm -f --
 
 mkdir -p ${D}/etc/rc.d/rc3.d/
 mkdir -p ${D}/etc/rc.d/rc5.d/
 ln -s ../init.d/calendar-serviced.sh ${D}/etc/rc.d/rc3.d/S85calendar-serviced
 ln -s ../init.d/calendar-serviced.sh ${D}/etc/rc.d/rc5.d/S85calendar-serviced
 
 mkdir -p ${D}${systemd_unitdir}/user/tizen-middleware.target.wants
 install ${S}/packaging/calendar.service ${D}${systemd_unitdir}/user/
 ln -s ../calendar.service ${D}${systemd_unitdir}/user/tizen-middleware.target.wants/
 
 
}

pkg_postinst_${PN}() {
    #!/bin/sh -e

    [ "x$D" == "x" ] && ldconfig

}

pkg_postrm_${PN}() {
    #!/bin/sh -e

    [ "x$D" == "x" ] && ldconfig
}

PACKAGES = "${PN}-dbg ${PN}-doc ${PN}-locale"
PACKAGES += " calendar-service "
PACKAGES += " calendar-service-dev "

calendar-service_files = ""
calendar-service_files += "${prefix}/bin/calendar-serviced*"
calendar-service_files += "${prefix}/lib/libcalendar-service2.so.*"
calendar-service_files += "/etc/rc.d/init.d/calendar-serviced.sh"
calendar-service_files += "/etc/rc.d/rc3.d/S85calendar-serviced"
calendar-service_files += "/etc/rc.d/rc5.d/S85calendar-serviced"
calendar-service_files += "/usr/share/calendar-svc/dft-calendar"
calendar-service_files += "${systemd_unitdir}/user/calendar.service"
calendar-service_files += "${systemd_unitdir}/user/tizen-middleware.target.wants/calendar.service"
MANIFESTFILES_${PN} = "calendar-service.manifest"

calendar-service-dev_files = ""
calendar-service-dev_files += "${prefix}/include/calendar-service/*.h"
calendar-service-dev_files += "${prefix}/include/calendar-service2/*.h"
calendar-service-dev_files += "${prefix}/lib/*.so"
calendar-service-dev_files += "${prefix}/lib/pkgconfig/calendar.pc"
calendar-service-dev_files += "${prefix}/lib/pkgconfig/calendar-service2.pc"
MANIFESTFILES_${PN}-dev = "calendar-service.manifest"

FILES_${PN} = "${calendar-service_files}"
FILES_${PN}-dev = "${calendar-service-dev_files}"

PKG_calendar-service= "calendar-service"
PKG_calendar-service-dev= "calendar-service-dev"

require calendar-service-extraconf.inc
