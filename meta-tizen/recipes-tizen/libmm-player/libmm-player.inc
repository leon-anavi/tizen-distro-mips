DESCRIPTION = "Multimedia Framework Player Library"
HOMEPAGE = "http://nohomepage.org"
SECTION = "System/Libraries"
LICENSE = "Apache-2.0"

SRC_URI = ""

S = "${WORKDIR}/git"

inherit autotools-brokensep

BBCLASSEXTEND = ""
PROVIDES = ""

#PROVIDES by libmm-player-dev
PROVIDES += "libmm-player-dev"


#PROVIDES by libmm-player


RDEPENDS = ""
#RDEPENDS of libmm-player-dev (${PN}-dev)
RDEPENDS_${PN}-dev += "libmm-player"


DEPENDS = ""
#DEPENDS of libmm-player
DEPENDS += "libmm-sound"
DEPENDS += "gstreamer1.0-plugins-base"
DEPENDS += "audio-session-manager"
DEPENDS += "libmm-common"
DEPENDS += "iniparser"
DEPENDS += "gstreamer1.0"
DEPENDS += "libmm-utility"
DEPENDS += "vconf"
DEPENDS += "libmm-ta"
DEPENDS += "libmm-session"
DEPENDS += "icu"

do_prep() {
 cd ${S}
 chmod -Rf a+rX,u+w,g-w,o-w ${S}
 #setup -q
 cp ${S}/packaging/libmm-player.manifest .
 
 
}
do_patch_append() {
    bb.build.exec_func('do_prep', d)
}

do_configure() {
}

do_compile() {
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
PACKAGES += " libmm-player-dev "
PACKAGES += " libmm-player "

libmm-player-dev_files = ""
libmm-player-dev_files += "${prefix}/lib/*.so"
libmm-player-dev_files += "${prefix}/include/mmf/*.h"
libmm-player-dev_files += "${prefix}/lib/pkgconfig/*"
MANIFESTFILES_${PN}-dev = "libmm-player.manifest"

libmm-player_files = ""
libmm-player_files += "${prefix}/lib/*.so.*"
MANIFESTFILES_${PN} = "libmm-player.manifest"

FILES_${PN}-dev = "${libmm-player-dev_files}"
FILES_${PN} = "${libmm-player_files}"

PKG_libmm-player-dev= "libmm-player-dev"
PKG_libmm-player= "libmm-player"

require libmm-player-extraconf.inc
