#!/usr/bin/env bash
# Interactive launcher for reverse-proxy scenarios.
#
# Walks the user through a small wizard:
#   1. pick a reverse proxy (apache-httpd/{http,ajp,https,ajp-https}, nginx/{http,https})
#   2. pick a scenario inside it (fuzzy filter; Esc returns to step 1)
#   3. action menu: Run (with/without logs), Docs (README), Back, Cancel
#
# Ctrl-C stops the containers (`docker compose stop`). After that you are
# asked whether to also `docker compose down -v` — pass --compose-down on the
# command line to skip the prompt and always tear down.

set -euo pipefail

force_down=0
silent=0
scenario_arg=""
while [[ $# -gt 0 ]]; do
    case "$1" in
        --compose-down) force_down=1 ;;
        --silent) silent=1 ;;
        -h|--help)
            cat <<EOF
Usage: $0 [--compose-down] [--silent] [<proxy>/<scenario>]

  <proxy>/<scenario>  Run this scenario directly (e.g. apache-httpd/http/root-context).
                      When omitted, an interactive picker is shown.
  --silent            Do not stream docker compose logs (run detached).
  --compose-down      On Ctrl-C, run \`docker compose down -v\` without asking.
EOF
            exit 0 ;;
        --) shift; break ;;
        -*)
            echo "unknown argument: $1" >&2
            exit 2 ;;
        *)
            if [[ -n $scenario_arg ]]; then
                echo "unexpected positional argument: $1" >&2
                exit 2
            fi
            scenario_arg="$1"
            ;;
    esac
    shift
done

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
catalog="$repo_root/scenarios.tsv"

command -v docker >/dev/null || { echo "docker is not installed"; exit 1; }
[[ -f $catalog ]] || { echo "scenarios.tsv not found at $catalog" >&2; exit 1; }

# `gum` is only required for the interactive flow.
if [[ -z $scenario_arg ]]; then
    command -v gum >/dev/null || { echo "gum is not installed (https://github.com/charmbracelet/gum)"; exit 1; }
fi

# OSC-8 hyperlink so terminals that support it make the URL clickable.
osc8_link() {
    local url="$1" text="${2:-$1}"
    printf '\e]8;;%s\e\\%s\e]8;;\e\\\n' "$url" "$text"
}

# Look up a column from scenarios.tsv ($2 = description, $3 = paths) by key.
catalog_field() {
    local key="$1" col="$2"
    awk -F'|' -v k="$key" -v c="$col" '
        /^[[:space:]]*#/ { next }
        /^[[:space:]]*$/ { next }
        {
            gsub(/^[[:space:]]+|[[:space:]]+$/, "", $1)
            if ($1 == k) {
                gsub(/^[[:space:]]+|[[:space:]]+$/, "", $c)
                print $c
                exit
            }
        }
    ' "$catalog"
}
description_for() { catalog_field "$1" 2; }
paths_for()       { catalog_field "$1" 3; }
short_for()       { catalog_field "$1" 4; }

show_logs=1
[[ $silent -eq 1 ]] && show_logs=0

if [[ -n $scenario_arg ]]; then
    # --- Non-interactive: scenario path given on the command line ---------
    key="${scenario_arg#./}"
    key="${key%/}"
    scenario_dir="$repo_root/$key"
    if [[ ! -f $scenario_dir/docker-compose.yml ]]; then
        echo "scenario not found: $key (no docker-compose.yml at $scenario_dir)" >&2
        exit 1
    fi
else
    # --- Wizard: proxy → scenario (with filter) → action ------------------
    # State machine; each step can return to the previous one via Esc/cancel
    # (gum exits non-zero on Esc, which we trap with `if !` to keep the
    # script alive under `set -euo pipefail`).
    step=1
    while :; do
        # Wipe leftover content (previous panels, gum's "nothing selected"
        # message on Esc, etc.) so each step renders on a clean screen.
        clear
        case $step in
            1)
                if ! proxy=$(printf '%s\n' \
                        "apache-httpd/http" \
                        "apache-httpd/ajp" \
                        "apache-httpd/https" \
                        "apache-httpd/ajp-https" \
                        "nginx/http" \
                        "nginx/https" \
                        | gum filter \
                            --header "Reverse proxy (type to filter, Esc to quit):" \
                            --placeholder "Filter proxies…"); then
                    exit 0
                fi
                [[ -z $proxy ]] && exit 0
                step=2
                ;;
            2)
                mapfile -t scenarios < <(
                    cd "$repo_root/$proxy" && \
                    for d in */; do
                        [[ -f "${d}docker-compose.yml" ]] && echo "${d%/}"
                    done | sort
                )
                [[ ${#scenarios[@]} -gt 0 ]] || { echo "no runnable scenarios under $proxy" >&2; exit 1; }

                # Pad scenario names to a fixed column so the second column
                # (short tag) lines up. Two spaces separate the two columns
                # so we can recover the name with `${selection%% *}` below.
                max=0
                for s in "${scenarios[@]}"; do (( ${#s} > max )) && max=${#s}; done
                declare -a scenario_items=()
                for s in "${scenarios[@]}"; do
                    tag="$(short_for "$proxy/$s")"
                    [[ -z $tag ]] && tag="$(description_for "$proxy/$s")"
                    printf -v row "%-*s  %s" "$max" "$s" "${tag:-(no description)}"
                    scenario_items+=( "$row" )
                done

                if ! selection=$(printf '%s\n' "${scenario_items[@]}" \
                        | gum filter \
                            --header "Scenario in $proxy (type to filter, Esc to go back):" \
                            --placeholder "Filter scenarios…"); then
                    step=1; continue
                fi
                [[ -z $selection ]] && { step=1; continue; }
                scenario="${selection%% *}"
                key="$proxy/$scenario"
                scenario_dir="$repo_root/$key"
                step=3
                ;;
            3)
                if [[ $silent -eq 1 ]]; then
                    show_logs=0
                    break
                fi
                full_desc="$(description_for "$key")"
                # gum style does not wrap long lines — pre-wrap the description so it
                # stays inside the rounded border on narrow terminals.
                term_cols=$(tput cols 2>/dev/null || echo 80)
                box_width=$(( term_cols < 100 ? term_cols : 100 ))
                inner_width=$(( box_width - 6 ))   # border (2) + padding "1 2" (2*2)
                (( inner_width < 20 )) && inner_width=20
                wrapped_desc=$(printf '%s\n' "${full_desc:-(no description)}" \
                    | fold -s -w "$inner_width")
                gum style --border rounded --padding "1 2" --foreground 212 \
                    "$key" "" "$wrapped_desc"
                if ! action=$(gum choose --header "Action (Esc to cancel):" \
                        "Run (with logs)" \
                        "Run (without logs)" \
                        "Docs" \
                        "← Back" \
                        "Cancel"); then
                    echo "cancelled."; exit 0
                fi
                case "$action" in
                    "Run (with logs)")    show_logs=1; break ;;
                    "Run (without logs)") show_logs=0; break ;;
                    "Docs")
                        if [[ -f $scenario_dir/README.md ]]; then
                            gum pager < "$scenario_dir/README.md"
                        else
                            echo "(no README at $scenario_dir/README.md)" && sleep 1
                        fi
                        ;;
                    "← Back") step=2 ;;
                    ""|"Cancel") echo "cancelled."; exit 0 ;;
                esac
                ;;
        esac
    done
fi

# --- Step 6: banner with clickable URL(s) --------------------------------
paths_csv=$(paths_for "$key")
[[ -z $paths_csv ]] && paths_csv="/"

if command -v gum >/dev/null; then
    gum style --border rounded --padding "1 2" --foreground 212 --bold "$key"
else
    echo "=== $key ==="
fi
# HTTPS scenarios publish the proxy on 9443 instead of 9090.
proxy_dir="${key%/*}"
case "$proxy_dir" in
    */https|*/ajp-https) origin="https://localhost:9443" ;;
    *)                   origin="http://localhost:9090" ;;
esac
echo "Open in your browser:"
IFS=',' read -r -a paths <<< "$paths_csv"
for p in "${paths[@]}"; do
    p_trimmed="${p#"${p%%[![:space:]]*}"}"
    p_trimmed="${p_trimmed%"${p_trimmed##*[![:space:]]}"}"
    [[ -z $p_trimmed ]] && continue
    osc8_link "${origin}${p_trimmed}"
done
echo
echo "Press Ctrl-C to stop."
echo

# --- Step 7: run the scenario --------------------------------------------
cd "$scenario_dir"

cleanup_done=0
cleanup() {
    [[ $cleanup_done -eq 1 ]] && return 0
    cleanup_done=1
    echo
    echo "Stopping containers..."
    docker compose stop >/dev/null 2>&1 || true
    if [[ $force_down -eq 1 ]]; then
        echo "Running docker compose down -v (forced by --compose-down)..."
        docker compose down -v
    elif command -v gum >/dev/null \
            && gum confirm "Also remove containers (docker compose down -v)?" --default=no; then
        docker compose down -v
    fi
}
trap cleanup EXIT

if [[ $show_logs -eq 1 ]]; then
    # Foreground compose: Ctrl-C reaches both compose and the script.
    # Compose handles SIGINT and exits gracefully; the trap below keeps the
    # script alive long enough for the EXIT handler to ask about teardown.
    trap 'true' INT
    docker compose up || true
else
    docker compose up -d >/dev/null
    trap 'exit 0' INT
    if command -v gum >/dev/null; then
        gum spin --title "$key running. Press Ctrl-C to stop." \
            -- bash -c 'while :; do sleep 60; done' || true
    else
        echo "$key running. Press Ctrl-C to stop."
        while :; do sleep 60; done
    fi
fi
