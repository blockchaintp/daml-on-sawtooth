# Contributing

Broadly speaking, the daml-on-sawtooth project is meant to remain compatible with the contribution guidelines of  [Hyperledger Sawtooth](https://sawtooth.hyperledger.org) project found [here](https://sawtooth.hyperledger.org/docs/core/releases/latest/community/contributing.html). If we do not have specific guidance in this document, you will likely be fine following that guide.

## Commit Process

daml-on-sawtooth is Apache 2.0 licensed and accepts contributions via [GitHb](https://github.com) pull requests.

* Fork this repository and make any changes in a branch of that fork
* Pull requests should be made from that branch+fork targeting the master branch of [blockchaintp/daml-on-sawtooth](https://github.com/blockchaintp/daml-on-sawtooth)
* Pull requests must be up-to-date vs master in order to be merged, do not use the "Update Branch" button on github. Instead rebase your branch vs master.
* Follow the [Seven Rules](https://chris.beams.io/posts/git-commit/#seven-rules) of good commits
* Rafactoring and enhancements should be at least in separate commits if not separate pull request

## Signed-off-by & DCO

We require compliance with [DCO](http://developercertificate.org/) for every commit.  Therefore pull requests will be blocked from merging until all commits include a "Signed-off-by" line in the commit message.  This is easily done by using `git commit -s`

## Signed commits

We require each commit to be GPG signed (`git commit -S`). This means:

1. Your commit author email address must match an address you have on GitHub
1. The GPG key you sign the commit must be for the same email address you used to author the commit

Follow the [guide](https://help.github.com/en/articles/signing-commits) on github to set up GPG signing for your account

## Pull Request Approval

As of now we require at least one [CODEOWNER](./CODEOWNER.md) approval to merge any PR.
