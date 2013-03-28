#!/bin/bash

function __exit_error {
  echo "An error has occured"  
  __pause
  exit 1
}

function __exit_success {
  __out_title "SCRIPT SUCCESSFULLY COMPLETED"
  __pause
  exit 0
}

function __pause {
  read -p "Press Enter to continue...."
}

function __out_title {
  printf "\n============================$1============================\n"
}

function __create_dev_branch {
  __verify_in_git_dir
  
  __out_title "CREATING DEV BRANCH"
  git checkout -b dev
}

#verify in git directory
function __verify_in_git_dir {  
  #git status &>/dev/null
  local GIT_DIR=`ls -la | grep -cE " .git$"`
  
  __VERIFY_IN_GIT_DIR_RESULT=true
  
  if [ "$GIT_DIR" != "1" ]; then    
    __VERIFY_IN_GIT_DIR_RESULT=false
    
    if [ "$1" == "true" ] || [ -z "$1" ]; then
      echo "Please navigate to a valid git directory and re-run script"
      __exit_error         
    fi
  fi 
}

function __verify_git_url () {
  local IS_HTTPS_URL=`__verify_git_https_url "$1"`
  local IS_READ_ONLY_URL=`__verify_git_read_only_url "$1"`
  local IS_SSH_URL=`__verify_git_ssh_url "$1"`
  
   if [ $IS_HTTPS_URL == true ] || [ $IS_READ_ONLY_URL == true ] || [ $IS_SSH_URL == true ]; then
    echo true
   else
    echo false
   fi
}

function __verify_git_https_url {
  local MATCH_HTTPS=`echo "$1" | grep "^https://"`
  
  if [ "$MATCH_HTTPS" == "" ]; then
    echo false
  else
    echo true   
  fi
}

function __verify_git_ssh_url {
  local MATCH_SSH=`echo "$1" | grep "^git@github.com"`
  
  if [ "$MATCH_SSH" == "" ]; then
    echo false
  else
    echo true   
  fi
}

function __verify_git_read_only_url {
  local MATCH_READ_ONLY=`echo "$1" | grep "^git://"`
  
  if [ "$MATCH_READ_ONLY" == "" ]; then
    echo false
  else
    echo true   
  fi
}

function __create_remote_upstream {
  __verify_in_git_dir
  
  if [ $1 == true ]; then
    __out_title "CREATING REMOTE UPSTREAM LOCATION"
  fi
  
  printf "\nPlease enter url of the upstream repository [usually READ-ONLY]. (esc): "
  read IN 
  
  if [ "$IN" == "esc" ]; then
    __exit_success
  else
    local IS_GIT_URL=`__verify_git_url "$IN"`
        
    if [ $IS_GIT_URL == false ]; then
      echo "URL does not match a git url pattern"
      __create_remote_upstream false
    else
      git remote add upstream "$IN"
    fi    
  fi
}

function __stage_modified_files {
  __stage_files " M " "MODIFICATION"
}

function __stage_added_files {
  __stage_files " A " "ADDITION"
}

function __stage_removed_files {
  __stage_files " D " "DELETION"
}

function __stage_files {
  local STATUS=`git status --short`
  local REGEX="^$1.+$"
  local NUM_MODS=`echo "$STATUS" | grep -c -E "$REGEX"`
  local MODIFICATIONS=`echo "$STATUS" | grep -E "$REGEX"`
  
  if [ "$NUM_MODS" != "0" ]; then    
    __out_title "ADDING $2 CHANGES"    
    echo "$MODIFICATIONS"
    
    local DO_ALL=false
  
    for i in $(eval echo {1..$NUM_MODS}); do      
      local FILE=`echo "$MODIFICATIONS" | head -$i | tail -1 | sed "s/^$1//g"`
	
      #prompt for user input
      if [ $DO_ALL == false ]; then
	printf "\n[[--$FILE--]] Stage for $2? (y, n, esc, all): "
	read IN
      fi  
      
      if [ "$IN" == "all" ]; then
	local DO_ALL=true
      fi
      
      #read user input of short circuit if DO_ALL is set to true
      if [ $DO_ALL == true ] || [ "$IN" == "y" ]; then
	
	if [ "$1" == " D " ]; then
	  git checkout "$FILE"
	  git rm "$FILE"
	else
	  git add "$FILE"	
	fi
	
	echo "[[--$FILE--]] Staged for $2"
	
      elif [ "$IN" == "n" ]; then
	echo "[[--$FILE--]] skipped"
	
      elif [ "$IN" == "esc" ]; then
	break    
	
      else
	echo "Invalid input"
      fi
      
    done  
  fi    
}

function git_clone_setup {
  __out_title "GIT CLONE SETUP"
  printf "\nAre you in the location where you want your git directory cloned? (y,n): "
  read IN
  
  if [ "$IN" == "y" ]; then
    printf "Enter url to clone: "
    read IN
    
    local IS_GIT_URL=`__verify_git_url "$IN"`
    
    if [ $IS_GIT_URL == false ]; then
      echo "$IN is not a valid git url"
      __exit_error
    fi
    
    local CLONE_DIR=`git clone "$IN"`
    local DIR=`echo "$CLONE_DIR" | grep -E -o "'\S+'" | tr -d "'"`
    cd "$DIR"
  fi
  
  __git_setup
  
  __exit_success
}

function __git_setup {
  
  local DEV_BRANCH_CREATED=`git branch | grep "dev$"`
  if [ "$DEV_BRANCH_CREATED" == "" ]; then
    __create_dev_branch
  fi
  
  local UPSTREAM_REMOTE_SET=`git remote show | grep "^upstream$"`
  if [ "$UPSTREAM_REMOTE_SET" != "upstream" ]; then
    __create_remote_upstream true
  fi
}

function git_update_all {
  
  printf "\nAre you in the root directory where all sub-directories are git directories? (y,n): "
  read IN
  
  if [ "$IN" == "y" ]; then
    local REGEX="^d.+$"
    local NUM_DIR=`ls -l | grep -coE "$REGEX"`
    local RESULT=`ls -l | grep -oE "$REGEX"`
    
    for i in $(eval echo {1..$NUM_DIR}); do
      local LINE=`echo "$RESULT" | head -$i | tail -1 | grep -oE " \S+$" | sed "s/^ //g"`
      
      __out_title "UPDATING '$LINE'"
      
      # navigate into dir
      cd "$LINE"
      
      if [ "$?" == "0" ]; then
	# update git 
	git_update false
	
	# jump back to root dir
	cd ..
	
	if [ "$__VERIFY_IN_GIT_DIR_RESULT" == "false" ]; then
	  echo "$LINE is not a git dir" 
	fi
      fi
    done
  fi
}

# update the current directory with upstream
function git_update {
  __verify_in_git_dir $1
    
  if [ "$__VERIFY_IN_GIT_DIR_RESULT" == "true" ]; then
    #setup local 
    __git_setup
    
    #stash changes
    __out_title "STASHING CHANGES"
    git stash
      
    #checkout the master branch
    __out_title "SWITCHING TO MASTER BRANCH"
    git checkout master
    
    #pull from upstream master
    __out_title "PULLING FROM UPSTREAM/MASTER"
    git pull upstream master 
	
    #rebase dev branch with master
    __out_title "REBASING DEV BRANCH"
    git checkout dev
    git rebase master
    
    #pop changes over rebased dev branch
    __out_title "POPPING STASHED CHANGES"
    git stash pop    
  fi
}

# Updates the master branch 
function git_pre_commit {    
  __verify_in_git_dir
  
  git_update
  
  printf "\nIf there are no merge conflicts, would you like commit your changes and push to origin? (y,n): "
  read IN
  
  if [ "$IN" == "y" ]; then
    git_commit
  else
    __exit_success
  fi  
}

# Adds the changed files to be committed
function git_commit {
  __verify_in_git_dir
  
  __out_title "LIST OF CHANGES"
  git status
  __pause
  
  __stage_modified_files
  __stage_added_files
  __stage_removed_files
  
  __out_title "LIST OF CHANGES"
  git status
  __pause
  
  printf "\nWould you like to commit and push staged changes? (y, n): "
  read IN 
  
  if [ "$IN" == "y" ]; then       
    git checkout dev &>/dev/null
  
  __out_title "COMMITTING CHANGES"
    git commit
    
    __out_title "PUSHING CHANGES TO ORIGIN"
    git push origin dev
  fi
  
  __exit_success
}