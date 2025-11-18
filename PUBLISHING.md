# Publishing browser-reload

Instructions for publishing new versions of the browser-reload library to GitHub.

## Prerequisites

- Git repository with commits ready to publish
- Push access to the remote repository

## Publishing a New Release

Just run:

```bash
make release
```

This automated process will:
1. Show git status and recent commits
2. Push commits to `origin/main`
3. Delete old `LATEST` tag (local and remote)
4. Create new `LATEST` tag at current HEAD
5. Push `LATEST` tag to remote
6. Display installation instructions

### Manual Process (if needed)

If you can't use the Makefile:

```bash
# 1. Commit your changes
git add .
git commit -m "Your commit message"

# 2. Push to main
git push origin main

# 3. Update LATEST tag
git tag -d LATEST 2>/dev/null || true
git push origin :refs/tags/LATEST 2>/dev/null || true
git tag -a LATEST -m "Latest release - $(date '+%Y-%m-%d %H:%M:%S')"
git push origin LATEST
```

## Installation for Users

Users always reference the `LATEST` tag in their `deps.edn`:

```clojure
{:deps {browser-reload/browser-reload
         {:git/url "https://github.com/realgenekim/browser-reload"
          :git/tag "LATEST"}}}
```

When you run `make release`, the `LATEST` tag moves to the current commit, so users automatically get the newest version next time they clear their classpath cache.

## Updating Consumer Projects

After publishing a new release with `make release`, consumers get the update by clearing their classpath cache:

```bash
# In consumer project
rm -rf .cpcache
clj -M:web  # Or whatever starts your app
```

Since they reference `:git/tag "LATEST"`, clearing `.cpcache` fetches the updated LATEST tag.

## Release Checklist

Before running `make release`:

- [ ] All tests pass: `make runtests-once`
- [ ] README is up to date
- [ ] CHANGELOG is updated
- [ ] All changes committed

After publishing:

- [ ] Verify LATEST tag appears on GitHub
- [ ] Test in consumer project (rm -rf .cpcache && clj ...)

## Troubleshooting

### Release fails with "already up-to-date"

If `make release` fails because there are no new commits:
- Make sure you've committed your changes: `git add . && git commit -m "..."`
- The LATEST tag will still update to the current HEAD

### Consumers can't fetch LATEST tag

If users see:
```
Error building classpath. Could not find artifact...
```

Solutions:
1. Verify LATEST tag exists on GitHub: `git ls-remote --tags origin`
2. Ensure commits are pushed: `git push origin main`
3. Re-run: `make release`

## See Also

- [deps.edn Git Dependencies](https://clojure.org/guides/deps_and_cli#_using_git_libraries)
- [Semantic Versioning](https://semver.org/)
- [GitHub CLI Documentation](https://cli.github.com/manual/)
