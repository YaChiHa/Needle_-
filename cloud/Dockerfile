FROM python:3.12-slim

ARG NEEDLE_REV=d6c7d6a2e9655505e0f8e23339356dd1705900eb
ENV PORT=8000
ENV NEEDLE_TELEMETRY=0
ENV DO_NOT_TRACK=1
WORKDIR /opt/needle

RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates curl \
    && rm -rf /var/lib/apt/lists/*

RUN curl --fail --location --retry 3 "https://huggingface.co/Cactus-Compute/needle3/resolve/${NEEDLE_REV}/linux-x86_64/needle" -o /opt/needle/needle \
    && curl --fail --location --retry 3 "https://huggingface.co/Cactus-Compute/needle3/resolve/${NEEDLE_REV}/needle3.cact" -o /opt/needle/needle3.cact \
    && chmod 0755 /opt/needle/needle

COPY cloud/tools.json /opt/needle/tools.json
COPY cloud/server.py /opt/needle/server.py

EXPOSE 8000
CMD ["python3", "/opt/needle/server.py"]
