#!/bin/bash

set -eu

readonly output_label='##################### OUTPUT #####################'

readonly result_file="$(mktemp)"
readonly pict_all_pair_output_file="$(mktemp)"
readonly pict_cover_all_output_file="$(mktemp)"

trap on_exit EXIT
function on_exit {
  rm "${result_file}" "${pict_all_pair_output_file}" "${pict_cover_all_output_file}"
}

function main {
  local pict_file="$1" nr_of_column

  cat "${pict_file}" | pict > "${pict_all_pair_output_file}"

  nr_of_column="$(count_column "${pict_all_pair_output_file}")"

  # -o: オプションで条件数と同じ数を指定することで全網羅パターンを出力できる。（デフォルトではペアワイズ法によりケースが絞られる）
  # 参考：
  # ペアワイズ法によるテストケース抽出ツール「PICT」を使ってテストケースを85%削減する - Qiita
  # https://qiita.com/bremen/items/6eceddc534d87fc797cc
  cat "${pict_file}" | pict -o:${nr_of_column} > "${pict_cover_all_output_file}"

  {
    cat "${pict_file}" | remove_output
    echo "${output_label}"
  } > "${result_file}"
  {
    echo "# 全組み合わせ網羅ケース:"
    normalize_pict_output "${pict_cover_all_output_file}" | decorate_table
    echo "#"
    echo "# 条件重複ケース（存在する場合は「結果の条件」の定義に漏れがある）:"
    normalize_pict_output "${pict_cover_all_output_file}" | print_repeated_factors | decorate_table
    echo "#"
    echo "# オールペア（2因子間網羅）ケース："
    normalize_pict_output "${pict_all_pair_output_file}" | decorate_table
  } | tee -a "${result_file}"

  cat "${result_file}" > "${pict_file}"
}

function count_column {
  local pict_output_file="$1"
  head -n 1 "${pict_output_file}" | awk -v FS='\t' '{ print NF }'
}

function remove_output {
  awk -v output_label="${output_label}" '
    $0 == output_label {
      exit 0
    }
    {
      print
    }
  '
}

function normalize_pict_output {
  local output_file="$1"
  cat <(head -n1 "${output_file}") <(sort -t $'\t' <(sed '1d' "${output_file}")) \
    | awk -v FS='\t' -v OFS='\t' '{ print (NR == 1) ? "No" : NR - 1, $0 }'
}

function decorate_table {
   column --table -s $'\t' | sed -E 's/^/# /'
}

function print_repeated_factors {
  awk -v FS='\t' -v OFS='\t' '
    BEGIN {
      current_factor = ""
      previous_factor = ""
      previous_all = ""
      previous_output = ""
      group_id = 1
    }
    {
        split($0, line, FS)
    }
    NR == 1 {
      for (i in line) {
        if (match(line[i], /^[^@]/) && i != 1) {
          factor_indexes[i] = i
        }
      }
      print "重複ID" OFS $0
    }
    NR > 1 {
      current_factor = ""
      for (i in factor_indexes) {
        current_factor = current_factor line[factor_indexes[i]] OFS
      }
      if (current_factor == previous_factor) {
        if (previous_all != previous_output) {
          print group_id OFS previous_all
        }
        print group_id OFS $0
        previous_output = $0
      } else {
        group_id = group_id + 1
      }
      previous_factor = current_factor
      previous_all = $0
    }
  '
}

function pict {
  docker run --rm -i ghcr.io/iceomix/pict "$@"
}

main "$@"
