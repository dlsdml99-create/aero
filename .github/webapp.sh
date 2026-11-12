#!/usr/bin/env bash
# 아이폰·PC용 웹앱 만들기: 앱 화면(index.html)을 site/app/ 에 넣는다.
set -euo pipefail
mkdir -p site/app
cp site/icon-180.png site/app/icon-180.png
# 홈 화면에 추가했을 때 아이콘·이름이 보이게, '미리보기' 안내는 '웹' 안내로
sed 's#<meta charset="utf-8">#<meta charset="utf-8"><link rel="apple-touch-icon" href="icon-180.png"><meta name="apple-mobile-web-app-title" content="Aero"><meta name="apple-mobile-web-app-capable" content="yes">#; s/미리보기에서는/웹에서는/g' \
  app/src/main/assets/index.html > site/app/index.html
grep -q 'apple-touch-icon' site/app/index.html
