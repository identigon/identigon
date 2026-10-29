#!/bin/sh
# doc-kit-check - verify a repository against the doc-kit specification (docs/spec.md in the kit).
#
# Usage:  sh tools/doc-kit-check.sh [check ...]
#         with no arguments, runs every check
#
# Checks: map    §2.2-2.7  the map is complete, consistent and customised
#         adr    §3.1      records are numbered, statused and structured
#         plan   §3.3      the plan has not become a graveyard
#
# Exits 0 if clean, 1 if anything failed. Run it by hand: nothing in this kit
# requires it, and a repository that holds these properties without ever
# running it is conformant (docs/spec.md §6).
#
# On Windows use `sh`, not `bash` - the `bash` on PATH is usually WSL's, which
# sees a different filesystem and will report every file missing.
#
# A file marked linguist-vendored in .gitattributes is skipped (§1). An unmapped
# gitignored file is a WARN, never a FAIL (§2.9), grouped by its top-level
# directory. Both need `git`; without it, every unmapped file is a FAIL.

set -eu

# Locale-independent collation: string comparison and sort order must not vary by runner.
LC_ALL=C
export LC_ALL

MAP=DOC-MAP.md
NL='
'
TAB='	'

failures=0
warnings=0
current=''

group() { current=$1; }
fail() {
	failures=$((failures + 1))
	printf '%s\n' "FAIL [$current] $1" >&2
	[ $# -gt 1 ] && printf '       %s\n' "$2" >&2
	return 0
}
warn() {
	warnings=$((warnings + 1))
	printf '%s\n' "WARN [$current] $1" >&2
	[ $# -gt 1 ] && printf '       %s\n' "$2" >&2
	return 0
}

# Asked once: on Windows each process costs tens of milliseconds, and the checks run in loops.
if command -v git >/dev/null 2>&1 && git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
	git_ok=0
else
	git_ok=1
fi
has_git() { return "$git_ok"; }

# within LIST ITEM - true when ITEM is a whole line of the newline-separated LIST. A case
# pattern, not grep, so a loop over files starts no process.
within() {
	case "$NL$1$NL" in *"$NL$2$NL"*) return 0 ;; esac
	return 1
}

# Filter stdin (one path per line) to the gitignored ones. Prints nothing when
# git is missing or this is not a work tree.
ignored_of() {
	if has_git; then
		git -c core.quotePath=false check-ignore --stdin || true
	else
		cat >/dev/null
	fi
}

# Filter stdin (one path per line) to the ones whose linguist-vendored attribute
# is set in .gitattributes (§1). Prints nothing without git.
vendored_of() {
	if has_git; then
		git -c core.quotePath=false check-attr --stdin linguist-vendored |
			sed -n -e 's/: linguist-vendored: set$//p' -e 's/: linguist-vendored: true$//p'
	else
		cat >/dev/null
	fi
}

# section FILE HEADING - the lines under a "## Heading" up to the next "## "
section() {
	awk -v h="$2" '$0 == h { f = 1; next } /^## / && f { exit } f' "$1"
}

# First backticked cell of each table row: the artifact's path.
# shellcheck disable=SC2016  # the backticks are literal Markdown, not command substitution
table_paths() {
	section "$MAP" "$1" | sed -n 's/^| `\([^`]*\)`.*/\1/p'
}

# The layout block is an indented tree; rebuild full paths with a prefix stack
# keyed on indentation. Only a bare `name/` line is a container. A directory
# followed by description text is a leaf: one artifact, matching a `name/*` row.
layout_paths() {
	awk '/^```text/ { f = 1; next } /^```/ { if (f) exit } f' "$MAP" | awk '
		{
			line = $0
			indent = 0
			while (indent < length(line) && substr(line, indent + 1, 1) ~ /[ \t]/) indent++
			rest = substr(line, indent + 1)
			if (rest == "") next

			while (depth > 0 && stack_indent[depth] >= indent) depth--

			prefix = ""
			for (i = 1; i <= depth; i++) prefix = prefix stack_name[i]

			if (rest ~ /^[^ \t]+\/[ \t]*$/) {
				split(rest, a, /[ \t]/)
				depth++
				stack_indent[depth] = indent
				stack_name[depth] = a[1]
				next
			}

			split(rest, a, /[ \t]/)
			print prefix a[1]
		}
	'
}

# A trailing / and a trailing /* denote the same artifact (§2.2).
norm() { sed 's:/\*$:/:'; }

# True when PATTERN matches at least one existing path.
pattern_exists() {
	# Unquoted, so the shell expands the glob.
	# shellcheck disable=SC2086
	set -- $1
	[ -e "$1" ]
}

# ---------------------------------------------------------------- map

check_map() {
	group map

	[ -f "$MAP" ] || { fail "no map at $MAP" "§2.1 requires one in the repository root"; return; }

	arts=$(table_paths '## Artifacts' | norm | sort)
	lifes=$(table_paths '## Lifecycle' | norm | sort)
	lays=$(layout_paths | norm | sort)

	[ -n "$arts" ] || fail "the artifacts table names nothing" "§2.2 - is the heading exactly '## Artifacts'?"

	# §2.2 - all three lists name the same set, artifacts table authoritative.
	# Globbing off: these lists contain patterns like docs/decisions/*.md, and an
	# unquoted loop would expand them into the files they match.
	set -f
	for a in $arts; do
		within "$lifes" "$a" ||
			fail "$a is in the artifacts table but not the lifecycle table" "§2.2"
		within "$lays" "$a" ||
			fail "$a is in the artifacts table but not the layout block" "§2.2"
	done
	for l in $lifes; do
		within "$arts" "$l" ||
			fail "$l is in the lifecycle table but not the artifacts table" "§2.2 - the table is authoritative"
	done

	# §2.3 - every artifact the map names exists.
	for a in $arts; do
		set +f
		pattern_exists "$a" ||
			fail "the map names $a but nothing matches it" "§2.3 - create it, or remove the row"
		set -f
	done

	# §2.4 - every documentation file appears in the map. Files below the conventional
	# archive (§5.1), templates (§2.8), and vendored files (§1) are not documentation files;
	# an archive elsewhere is covered by its map entry. Split on newlines only, so a path may
	# contain spaces.
	set +f
	files=$(find . -name '*.md' ! -path './.git/*' ! -path './docs/archive/*' ! -path './templates/*' | sed 's:^\./::' | sort)
	set -f
	unmapped=''
	oldifs=$IFS
	IFS=$NL
	for f in $files; do
		hit=1
		for a in $arts; do
			# shellcheck disable=SC2254
			case "$f" in $a|"${a%/}"/*) hit=0; break ;; esac
		done
		[ "$hit" -eq 0 ] || unmapped="$unmapped$f$NL"
	done
	vendored=$(printf '%s' "$unmapped" | vendored_of)
	if [ -n "$vendored" ]; then
		rest=''
		for f in $unmapped; do
			within "$vendored" "$f" || rest="$rest$f$NL"
		done
		unmapped=$rest
	fi
	ignored=$(printf '%s' "$unmapped" | ignored_of)
	for f in $unmapped; do
		within "$ignored" "$f" ||
			fail "$f is not named in the map" "§2.4 - add a row, or move it under an archive"
	done
	for g in $(printf '%s\n' "$ignored" | awk -F/ '
		NF {
			k = (NF > 1) ? $1 "/" : $0
			if (!(k in n)) { first[k] = $0; order[++c] = k }
			n[k]++
		}
		END { for (i = 1; i <= c; i++) { k = order[i]; print (n[k] == 1) ? first[k] : k " (" n[k] " files)" } }
	'); do
		IFS=$oldifs
		warn "$g is not named in the map (gitignored)" "§2.9 - advisory; add a row to map it"
	done
	IFS=$oldifs
	set +f

	# §2.7 - tense, mutability and audience come from closed sets: free text lets near-duplicates
	# escape on phrasing. Artifacts sharing all three are reported as a prompt to check, not a
	# failure (ADR-0002). Aliases, which hold no content of their own, are exempt. One awk pass
	# prints a tab-separated line per finding: a kind, the artifact, and the value.
	hits=$(section "$MAP" '## Artifacts' | grep '^| `' | grep -v '\*\*Alias\*\*' | awk -F' *[|] *' '
		BEGIN {
			split("present past future imperative explanatory", t, " ")
			for (i in t) tenses[t[i]] = 1
			n = split("rewritten in place|amendable|append-only|immutable|volatile|disposable", m, "|")
			for (i = 1; i <= n; i++) muts[m[i]] = 1
			split("owner contributor consumer end-user operator", r, " ")
			for (i in r) roles[r[i]] = 1

			exempt_pairs["DOC-MAP.md + docs/testing.md"] = 1
			exempt_pairs["README.md + docs/glossary.md"] = 1
			exempt_pairs["docs/quirks.md + docs/spec.md"] = 1
			exempt_pairs["docs/archive.md + docs/decisions.md"] = 1
		}
		function norm_pair(a, b) {
			return (a < b) ? (a " + " b) : (b " + " a)
		}
		function is_parent_child(p, c,  q) {
			if (p ~ /\.md$/) {
				q = p
				sub(/\.md$/, "", q)
				if (c ~ ("^" q "/")) return 1
			}
			return 0
		}
		function is_exempt_pair(a, b,  pa, pb) {
			if (norm_pair(a, b) in exempt_pairs) return 1
			if (is_parent_child(a, b) || is_parent_child(b, a)) return 1
			# Sibling members in the same docs/ container
			if (a ~ /^docs\/[^\/]+\// && b ~ /^docs\/[^\/]+\//) {
				split(a, pa, "/"); split(b, pb, "/");
				if (pa[2] == pb[2]) return 1
			}
			return 0
		}
		{
			art = $2
			gsub(/`/, "", art)
			if (!($4 in tenses)) print "tense\t" art "\t" $4
			if (!($5 in muts)) print "mut\t" art "\t" $5
			# Each role once, optionally marked "(agents)" or "(humans)", in any order: sorted
			# here so that order does not hide a shared triple.
			k = split($6, items, /, */)
			if (k == 0) print "aud\t" art "\t" $6
			for (i = 1; i <= k; i++) {
				role = items[i]
				sub(/ \((agents|humans)\)$/, "", role)
				if (!(role in roles)) print "aud\t" art "\t" items[i]
				for (j = i; j > 1 && items[j - 1] > items[j]; j--) {
					x = items[j]; items[j] = items[j - 1]; items[j - 1] = x
				}
			}
			# §2.8 - Product and template artifacts are exempt from instance duplicate-property
			# checks.
			if ($3 ~ /^\*\*(Product|Template)/) next

			aud = ""
			for (i = 1; i <= k; i++) aud = aud (i > 1 ? ", " : "") items[i]
			key = $4 " | " $5 " | " aud

			if (!(key in count)) {
				order[++c] = key
				count[key] = 0
			}
			idx = ++count[key]
			arts_by_key[key, idx] = art
		}
		END {
			for (i = 1; i <= c; i++) {
				key = order[i]
				narts = count[key]
				if (narts < 2) continue
				for (j = 1; j <= narts; j++) {
					for (k = j + 1; k <= narts; k++) {
						a1 = arts_by_key[key, j]
						a2 = arts_by_key[key, k]
						if (!is_exempt_pair(a1, a2)) {
							pair_key = norm_pair(a1, a2)
							if (!(pair_key in reported)) {
								reported[pair_key] = 1
								print "share\t" a1 ", " a2 "\t" key
							}
						}
					}
				}
			}
		}
	')
	set -f
	oldifs=$IFS
	IFS=$NL
	for hit in $hits; do
		IFS=$oldifs
		kind=${hit%%"$TAB"*}
		art=${hit#*"$TAB"}
		val=${art#*"$TAB"}
		art=${art%%"$TAB"*}
		case "$kind" in
			tense) fail "$art has tense '$val'" "§2.7 - present, past, future, imperative, explanatory" ;;
			mut) fail "$art has mutability '$val'" "§2.7 - see the permitted set" ;;
			aud) fail "$art has audience '$val'" "§2.7 - owner, contributor, consumer, end-user, operator" ;;
			share) warn "$art share [$val]" "§2.7 - merge them unless they hold different facts, or mark one an alias" ;;
		esac
		IFS=$NL
	done
	IFS=$oldifs
	set +f

	# §2.5 - customisation actually happened.
	grep -q 'This file is a template' "$MAP" &&
		fail "the map still carries template text" "§2.5"
	return 0
}

# ---------------------------------------------------------------- adr

check_adr() {
	group adr
	dir=docs/decisions
	check_citations
	[ -d "$dir" ] || return 0

	pattern_exists "$dir/*.md" || return 0

	# One awk pass over every record, printing one tab-separated line per failure: a kind, the
	# file, and the status where the message needs it. A placeholder - the template's {...} -
	# counts as missing. The template itself (0000-*) is skipped, but its number still counts.
	hits=$(awk '
		function flush() {
			if (f == "" || skip) return
			if (base !~ /^[0-9][0-9][0-9][0-9]-[a-z0-9]+(-[a-z0-9]+)*\.md$/) print "misnamed\t" f
			if (status == "") print "nostatus\t" f
			else if (status !~ /^(proposed|accepted|rejected|deprecated)$/) print "status\t" f "\t" status
			if (!date) print "nodate\t" f
			if (status != "" && status != "proposed") {
				if (!dm) print "nodm\t" f "\t" status
				if (!appr) print "noappr\t" f "\t" status
			}
			if (!h1) print "heading\t" f "\t## Context and Problem Statement"
			if (!h2) print "heading\t" f "\t## Considered Options"
			if (!h3) print "heading\t" f "\t## Decision Outcome"
		}
		function start(name) {
			f = name
			done[f] = 1
			base = f
			sub(/.*\//, "", base)
			skip = (base ~ /^0000-/ || base == "README.md")
			if (base ~ /^[0-9][0-9][0-9][0-9]-/) seen[substr(base, 1, 4)]++
			status = ""; date = dm = appr = h1 = h2 = h3 = 0
		}
		FNR == 1 { flush(); start(FILENAME) }
		status == "" && /^status: *"?[^"]*"? *$/ {
			s = $0
			sub(/^status: */, "", s)
			sub(/ *$/, "", s)
			gsub(/"/, "", s)
			status = s
		}
		/^date:/ { date = 1 }
		/^decision-makers: *[^ {]/ { dm = 1 }
		/^approval: *[^ {]/ { appr = 1 }
		index($0, "## Context and Problem Statement") { h1 = 1 }
		index($0, "## Considered Options") { h2 = 1 }
		index($0, "## Decision Outcome") { h3 = 1 }
		END {
			flush()
			# An empty file has no first line, so it is visited here.
			for (i = 1; i < ARGC; i++) if (!(ARGV[i] in done)) { start(ARGV[i]); flush() }
			for (k in seen) if (seen[k] > 1) print "dupe\t" k
		}
	' "$dir"/*.md)

	set -f
	oldifs=$IFS
	IFS=$NL
	for hit in $hits; do
		IFS=$oldifs
		kind=${hit%%"$TAB"*}
		f=${hit#*"$TAB"}
		arg=${f#*"$TAB"}
		f=${f%%"$TAB"*}
		case "$kind" in
			misnamed) fail "$f is misnamed" "§3.1 - NNNN-kebab-case-title.md" ;;
			nostatus) fail "$f has no status" "§3.1 - MADR front-matter" ;;
			status) fail "$f has status '$arg'" "§3.1 - proposed, accepted, rejected or deprecated" ;;
			nodate) fail "$f has no date" "§3.1" ;;
			nodm) fail "$f is '$arg' but names no decision-makers" "§3.1 - who decided?" ;;
			noappr) fail "$f is '$arg' but has no approval" "§3.1 - the owner's words, verbatim" ;;
			heading) fail "$f has no '$arg'" "§3.1 - MADR minimal template" ;;
			dupe) fail "ADR number $f is used more than once" "§3.1 - numbers are unique" ;;
		esac
		IFS=$NL
	done
	IFS=$oldifs
	set +f

	# §4.3 - the generated decisions index is up to date.
	idx="${dir}.md"
	if [ -f "$idx" ] && [ -f tools/doc-kit-index-decisions.sh ]; then
		sh tools/doc-kit-index-decisions.sh "${idx}.tmp" "$dir"
		if ! cmp -s "$idx" "${idx}.tmp"; then
			fail "$idx is out of date" "run 'sh tools/doc-kit-index-decisions.sh' to regenerate (§4.3)"
		fi
		rm -f "${idx}.tmp"
	elif [ -f "$dir/README.md" ] && [ -f tools/doc-kit-index-decisions.sh ]; then
		sh tools/doc-kit-index-decisions.sh "$dir/README.md.tmp" "$dir"
		if ! cmp -s "$dir/README.md" "$dir/README.md.tmp"; then
			fail "$dir/README.md is out of date" "run 'sh tools/doc-kit-index-decisions.sh $dir/README.md' to regenerate (§4.3)"
		fi
		rm -f "$dir/README.md.tmp"
	fi

	return 0
}

# §3.1 - every bare ADR-NNNN names a record in docs/decisions/, and no citation takes another form.
# Code - fenced blocks and inline spans - and the text of links to other sites are not citations.
# Vendored and gitignored files are not read.
check_citations() {
	set +f
	docs=$(find . -name '*.md' ! -path './.git/*' ! -path './docs/archive/*' ! -path './templates/*' | sed 's:^\./::' | sort)
	set -f
	skip=$(printf '%s\n' "$docs" | vendored_of; printf '%s\n' "$docs" | ignored_of)
	scan=''
	oldifs=$IFS
	IFS=$NL
	for f in $docs; do
		within "$skip" "$f" || scan="$scan$f$NL"
	done
	IFS=$oldifs

	# The file names arrive on stdin, one per line, so a name may contain spaces. Each hit is
	# a kind, the file, the line number and the token, separated by tabs.
	hits=$(printf '%s' "$scan" | awk '
		{
			name = $0
			fence = 0
			no = 0
			while ((getline raw < name) > 0) {
				no++
				if (raw ~ /^[ \t]*(```|~~~)/) { fence = !fence; continue }
				if (fence || raw ~ /^\[[^]]*\]: /) continue
				line = raw
				gsub(/`[^`]*`/, "", line)
				gsub(/\[[^]]*\]\(https?:[^)]*\)/, "", line)
				gsub(/\]\[[^]]*\]/, "]", line)
				while (match(line, /(^|[^A-Za-z0-9])[Aa][Dd][Rr][- ]?[0-9]+/)) {
					tok = substr(line, RSTART, RLENGTH)
					if (tok !~ /^[Aa]/) tok = substr(tok, 2)
					kind = tok ~ /^ADR-[0-9][0-9][0-9][0-9]$/ ? "cite" : "form"
					print kind "\t" name "\t" no "\t" tok
					line = substr(line, RSTART + RLENGTH)
				}
			}
			close(name)
		}
	')

	IFS=$NL
	for hit in $hits; do
		IFS=$oldifs
		kind=${hit%%"$TAB"*}
		rest=${hit#*"$TAB"}
		f=${rest%%"$TAB"*}
		rest=${rest#*"$TAB"}
		no=${rest%%"$TAB"*}
		tok=${rest#*"$TAB"}
		case "$kind" in
			cite)
				set +f
				pattern_exists "$dir/${tok#ADR-}-*.md" ||
					fail "$f:$no cites $tok, which does not exist" "§3.1 - a bare ID names a record here; link to another repository's"
				set -f
				;;
			form) fail "$f:$no cites '$tok'" "§3.1 - cite a record as ADR-NNNN" ;;
		esac
		IFS=$NL
	done
	IFS=$oldifs
	set +f
}

# ---------------------------------------------------------------- plan

check_plan() {
	group plan
	[ -f docs/plan.md ] || return 0

	# One awk pass: `done N` for a heading marked complete, `type N` for an entry (a `##` heading)
	# with no valid type tag beneath it. Lowercase "done" counts only when set off as a marker -
	# "(done)", "- done" - so a title like "Define done" passes.
	hits=$(awk '
		function flush() { if (h && !t) print "type " h }
		/^#+ / {
			l = tolower($0)
			if (l ~ /~~|\[x\]|[(\[](done|completed?)[)\]]|[-:] *(done|completed?) *$/ ||
				$0 ~ /(^|[^A-Za-z])(DONE|COMPLETED?)([^A-Za-z]|$)/) print "done " NR
		}
		/^## / { flush(); h = NR; t = 0; next }
		/^\*\*Type:\*\* (bug|debt|feature|docs)([ -]|$)/ { t = 1 }
		END { flush() }
	' docs/plan.md)

	# Fed from a variable, not a pipe: a piped `while` runs in a subshell, and increments to
	# `failures` would be lost.
	set -f
	oldifs=$IFS
	IFS=$NL
	for hit in $hits; do
		IFS=$oldifs
		no=${hit#* }
		case "$hit" in
			done*) fail "docs/plan.md:$no looks like a completed entry" "§3.3 - delete entries when done, do not annotate" ;;
			type*) fail "docs/plan.md:$no has no valid type tag" "§3.3 - bug, debt, feature or docs" ;;
		esac
	done
	IFS=$oldifs
	set +f
	return 0
}

# ---------------------------------------------------------------- main

[ $# -gt 0 ] || set -- map adr plan
for want in "$@"; do
	case "$want" in
		map) check_map ;;
		adr) check_adr ;;
		plan) check_plan ;;
		*) printf 'unknown check: %s (map, adr, plan)\n' "$want" >&2; exit 2 ;;
	esac
done

if [ "$failures" -eq 0 ]; then
	if [ "$warnings" -eq 0 ]; then
		printf 'doc-kit: checks passed (%s)\n' "$*"
	else
		printf 'doc-kit: checks passed (%s) - %s warning(s), see above\n' "$*" "$warnings"
	fi
else
	printf '\ndoc-kit: %s failure(s)' "$failures" >&2
	[ "$warnings" -eq 0 ] || printf ', %s warning(s)' "$warnings" >&2
	printf '\n' >&2
	exit 1
fi
