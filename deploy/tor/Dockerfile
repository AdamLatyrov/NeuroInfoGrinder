FROM alpine:3.20

RUN apk add --no-cache tor \
    && mkdir -p /var/lib/tor \
    && chown -R tor /var/lib/tor

COPY torrc /etc/tor/torrc

USER tor

HEALTHCHECK --interval=30s --timeout=5s --start-period=20s --retries=10 \
  CMD nc -z 127.0.0.1 9050 || exit 1

CMD ["tor", "-f", "/etc/tor/torrc"]
