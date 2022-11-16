#
# Copyright (c) 2021, Red Hat, Inc.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# This code is free software; you can redistribute it and/or modify it
# under the terms of the GNU General Public License version 2 only, as
# published by the Free Software Foundation.  Oracle designates this
# particular file as subject to the "Classpath" exception as provided
# by Oracle in the LICENSE file that accompanied this code.
#
# This code is distributed in the hope that it will be useful, but WITHOUT
# ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
# FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
# version 2 for more details (a copy is included in the LICENSE file that
# accompanied this code).
#
# You should have received a copy of the GNU General Public License version
# 2 along with this work; if not, write to the Free Software Foundation,
# Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
#
# Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
# or visit www.oracle.com if you need additional information or have any
# questions.
#

################################################################################
# Setup system configuration libraries
################################################################################
AC_DEFUN_ONCE([LIB_SETUP_SYSCONF_LIBS],
[
  ###############################################################################
  #
  # Check for the NSS library
  #
  AC_MSG_CHECKING([for NSS library directory])
  PKG_CHECK_VAR(NSS_LIBDIR, nss, libdir, [AC_MSG_RESULT([$NSS_LIBDIR])], [AC_MSG_RESULT([not found])])

  AC_MSG_CHECKING([whether to link the system NSS library with the System Configurator (libsysconf)])

  # default is not available
  DEFAULT_SYSCONF_NSS=no

  AC_ARG_ENABLE([sysconf-nss], [AS_HELP_STRING([--enable-sysconf-nss],
     [build the System Configurator (libsysconf) using the system NSS library if available @<:@disabled@:>@])],
  [
    case "${enableval}" in
      yes)
        sysconf_nss=yes
        ;;
      *)
        sysconf_nss=no
        ;;
    esac
  ],
  [
    sysconf_nss=${DEFAULT_SYSCONF_NSS}
  ])
  AC_MSG_RESULT([$sysconf_nss])

  USE_SYSCONF_NSS=false
  if test "x${sysconf_nss}" = "xyes"; then
      PKG_CHECK_MODULES(NSS, nss >= 3.53, [NSS_FOUND=yes], [NSS_FOUND=no])
      if test "x${NSS_FOUND}" = "xyes"; then
         AC_MSG_CHECKING([for system FIPS support in NSS])
         saved_libs="${LIBS}"
         saved_cflags="${CFLAGS}"
         CFLAGS="${CFLAGS} ${NSS_CFLAGS}"
         LIBS="${LIBS} ${NSS_LIBS}"
         AC_LANG_PUSH([C])
         AC_LINK_IFELSE([AC_LANG_PROGRAM([[#include <nss3/pk11pub.h>]],
                                         [[SECMOD_GetSystemFIPSEnabled()]])],
                        [AC_MSG_RESULT([yes])],
                        [AC_MSG_RESULT([no])
                        AC_MSG_ERROR([System NSS FIPS detection unavailable])])
         AC_LANG_POP([C])
         CFLAGS="${saved_cflags}"
         LIBS="${saved_libs}"
         USE_SYSCONF_NSS=true
      else
         dnl NSS 3.53 is the one that introduces the SECMOD_GetSystemFIPSEnabled API
         dnl in nss3/pk11pub.h.
         AC_MSG_ERROR([--enable-sysconf-nss specified, but NSS 3.53 or above not found.])
      fi
  fi
  AC_SUBST(USE_SYSCONF_NSS)
  AC_SUBST(NSS_LIBDIR)
])
