#!/usr/bin/env bash
set -o errexit
set -o errtrace
set -o pipefail

echo "T"

# KNOWN_HOSTS already contains an entry for github, so we do not want to exit if it's not set
if [[ -n $KNOWN_HOSTS ]]; then
   echo "$KNOWN_HOSTS" > ~/.ssh/known_hosts
fi

REPO_NAME=instances-repo
REPO_DIR=~/unipipe/$REPO_NAME
if [[ ! -d "$REPO_DIR" ]]; then
    cat > ~/.ssh/id_rsa <<< "${GIT_SSH_KEY}"
    chmod 700 ~/.ssh
    chmod 600 ~/.ssh/id_rsa
    cd ~/unipipe
    git config --global user.email "$GIT_USER_EMAIL"
    git config --global user.name "$GIT_USER_NAME"
    git clone "$GIT_REMOTE" "$REPO_NAME"
fi

cd $REPO_DIR

/usr/local/bin/unipipe git pull $REPO_DIR
/usr/local/bin/unipipe terraform $REPO_DIR
/usr/local/bin/unipipe git push $REPO_DIR -m "processed instances via unipipe terraform"
