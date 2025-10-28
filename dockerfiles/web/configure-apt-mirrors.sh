!#/bin/sh

if [ -n "$NEXUS_APT_MIRROR_DEBIAN_BOOKWORM_URL" ] && [ -n "$NEXUS_APT_MIRROR_SECURITY_DEBIAN_BOOKWORM_URL" ]; then \
      echo "Configuring APT to use Nexus mirrors" && \
      rm -f /etc/apt/sources.list && \
      rm -f /etc/apt/sources.list.d/* && \
      rm -rf /var/lib/apt/lists/* && \
      echo "deb $NEXUS_APT_MIRROR_DEBIAN_BOOKWORM_URL bookworm main contrib non-free non-free-firmware" >> /etc/apt/sources.list && \
      echo "deb $NEXUS_APT_MIRROR_SECURITY_DEBIAN_BOOKWORM_URL bookworm-security main contrib non-free non-free-firmware" >> /etc/apt/sources.list; \
    fi
