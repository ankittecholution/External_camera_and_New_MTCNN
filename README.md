# Contributing
When contributing to this repository, please first discuss the change you wish to make via issue, email, or any other method with the owners of this repository before making a change.

Please note we have a code of conduct, please follow it in all your interactions with the project.

## Pull Request Process
### Step-1:

* Navigate to the `https://github.com/Techolution/tsas_terminal`

* In the top-right corner of the page, click Fork.

* Under the repository name in your personal account, click Clone or download.

* In the Clone with HTTPs section, click to copy the clone URL from your personal repository.

* Open terminal and type git clone PERSONALURL. Here

* git clone `https://github.com/YOURUSERNAME/tsas_terminal.git`

This will create your local clone repository.

### Step-2

* You might fork a project in order to propose changes to the upstream, or original, repository. In this case, it's good practice to regularly sync your fork with the upstream repository. To do this, you'll need to use Git in terminal. `git remote` add upstream MASTERURL. Here
> git remote add upstream `https://github.com/Techolution/tsas_terminal.git` (Use only for 1st time)

* `git pull upstream`

Now you have the latest copy of the code in your local repository.

To verify the new upstream repository you've specified for your fork, type `git remote -v.` again in terminal. You should see the URL for your fork as origin, and the URL for the original repository as upstream.

### Next steps

* Creating branches: Branches allow you to build new features or test out ideas without putting your main project at risk.

* Opening pull requests: If you are hoping to contribute back to the original repository, you can send a request to the original author to pull your fork into their repository by submitting a pull request.

* git cherry-pick process:
Cherry picking in Git means to choose some specific commit from one branch and apply it onto another.

Let’s say you are working in a project where you are making changes in a branch called new-features. You have already made a few commits but want to move just one of them into the master branch.

From new-features branch run `git log --oneline` to get a better log of your commits history. Note that the commit hash is what we need to start the cherry picking.

Checkout the branch where you want to cherry pick the specific commits. In this case master branch: `git checkout master`

Now we can cherry pick from new-features branch:

`git cherry-pick <commit-hash>`

This will cherry pick the commit with hash and add it as a new commit on the master branch. Note: it will have a new (and different) commit ID in the master branch.

Now do `git push`

If you want to cherry pick more than one commit in one go, you can add their commit IDs separated by a space.

If the cherry picking gets halted because of conflicts, resolve them and `git cherry-pick --continue`

If you want to bail of this step out altogether, just type:` git cherry-pick --abort`

## Git commit conventions:
**Commit Message**: Ticket no. : Type (Scope) : Description
`Ticket no. : Jira card number`

Type:

```
fix - for bug fix
feat - for new feature
chore - for regular house keeping(removing unused code/ gitignore)
docs - for documentation/ comments
style - for changes in style
test - for test case implementation
improv - iteration of a feature, scope mandatory
BCHG - BREAKING CHANGES, with description of what the changes will break.
Scope : filename/ function name/ database name
Description : Brief description about the changes done.
```

Example - `git commit -m "FA-345 : sendData(JSONObject body) : Sending Image Data to Server`

Run:
`./commit_help.sh` to display all possible types during commit.

**NOTE :** If you update anything in future. Kindly add those changes to this file.
