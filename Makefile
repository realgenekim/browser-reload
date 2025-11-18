.PHONY: help runtests-once release

help:
	@echo "browser-reload Makefile targets:"
	@echo ""
	@echo "  make runtests-once  - Run compilation tests"
	@echo "  make release        - Create git tag and push to remote"
	@echo "                        (prompts for version and release notes)"
	@echo ""

runtests-once:
	@echo "Checking Clojure compilation..."
	@clj -M -e "(require 'browser-reload.core)" && echo "✅ Compilation successful" || (echo "❌ Compilation failed" && exit 1)

release:
	@echo "========================================="
	@echo "browser-reload Release Process"
	@echo "========================================="
	@echo ""
	@echo "Current status:"
	@git status --short
	@echo ""
	@echo "Recent commits:"
	@git log --oneline -5
	@echo ""
	@echo "This will:"
	@echo "  1. Push current commits to origin/main"
	@echo "  2. Create/update LATEST tag to current HEAD"
	@echo "  3. Force push LATEST tag (overwrites previous LATEST)"
	@echo ""
	@read -p "Continue? (y/N): " CONFIRM; \
	if [ "$$CONFIRM" != "y" ] && [ "$$CONFIRM" != "Y" ]; then \
		echo "Aborted."; \
		exit 1; \
	fi; \
	echo ""; \
	echo "Pushing commits to origin/main..."; \
	git push origin main; \
	echo ""; \
	echo "Deleting old LATEST tag (local and remote)..."; \
	git tag -d LATEST 2>/dev/null || true; \
	git push origin :refs/tags/LATEST 2>/dev/null || true; \
	echo ""; \
	echo "Creating new LATEST tag..."; \
	git tag -a LATEST -m "Latest release - $$(date '+%Y-%m-%d %H:%M:%S')"; \
	echo ""; \
	echo "Pushing LATEST tag..."; \
	git push origin LATEST; \
	echo ""; \
	COMMIT_SHA=$$(git rev-parse HEAD); \
	echo "========================================"; \
	echo "✅ LATEST tag published!"; \
	echo "========================================"; \
	echo ""; \
	echo "Users can add this to deps.edn:"; \
	echo ""; \
	echo "{:deps {browser-reload/browser-reload"; \
	echo "         {:git/url \"https://github.com/realgenekim/browser-reload\""; \
	echo "          :git/tag \"LATEST\"}}}"; \
	echo ""
