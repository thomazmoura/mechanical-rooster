# Multi-arch (amd64/arm64) image for the API. The SDK stage always runs on the
# build host's architecture and cross-compiles for $TARGETARCH, so building for
# a Raspberry Pi from an amd64 machine needs no QEMU emulation:
#
#   docker buildx build --platform linux/amd64,linux/arm64 -t <tag> --push backend/
FROM --platform=$BUILDPLATFORM mcr.microsoft.com/dotnet/sdk:10.0 AS build
ARG TARGETARCH
WORKDIR /src
COPY RelentlessBadger.Api/RelentlessBadger.Api.csproj RelentlessBadger.Api/
RUN dotnet restore RelentlessBadger.Api -a "$TARGETARCH"
COPY RelentlessBadger.Api/ RelentlessBadger.Api/
RUN dotnet publish RelentlessBadger.Api -a "$TARGETARCH" --no-restore -o /app

FROM mcr.microsoft.com/dotnet/aspnet:10.0
WORKDIR /app
COPY --from=build /app .
# appsettings.json sets Urls=http://0.0.0.0:5000, which wins over the base
# image's ASPNETCORE_HTTP_PORTS=8080.
EXPOSE 5000
ENTRYPOINT ["dotnet", "RelentlessBadger.Api.dll"]
