require libiri.inc

PRIORITY = "10"

LIC_FILES_CHKSUM ??= "file://${COMMON_LICENSE_DIR}/GPL-2.0;md5=801f80980d171dd6425610833a22dbe6"

SRC_URI += "git://review.tizen.org/platform/upstream/libiri;tag=363e9b8662c93c826fa1c33b8737d161b9491234;nobranch=1"

BBCLASSEXTEND = "native"
