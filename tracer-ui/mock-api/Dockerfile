FROM mhart/alpine-node:11.12
MAINTAINER kai@blockchaintp.com
RUN apk update
RUN apk upgrade
RUN apk add bash git
WORKDIR /app/api
COPY ./package.json /app/api/package.json
COPY ./yarn.lock /app/api/yarn.lock
RUN yarn install
COPY ./ /app/api
ENTRYPOINT ["yarn", "run", "serve"]
