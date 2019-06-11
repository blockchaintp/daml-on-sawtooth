FROM mhart/alpine-node:11.12
MAINTAINER kai@blockchaintp.com
RUN apk update
RUN apk upgrade
RUN apk add bash git
WORKDIR /app/frontend
COPY ./package.json /app/frontend/package.json
COPY ./yarn.lock /app/frontend/yarn.lock
RUN yarn install
COPY ./ /app/frontend
ENTRYPOINT ["yarn", "run", "develop"]
