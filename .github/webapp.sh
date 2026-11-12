#!/usr/bin/env bash
# 아이폰·PC용 웹앱 만들기: 앱 화면(index.html)을 site/app/ 에 넣는다.
# 보안: 웹앱에서 쓰는 PDF·바코드 부품을 바깥 CDN(cdnjs)에서 불러오지 않고,
#       npm 공식 저장소에서 받아(npm이 무결성 sha512 확인) 같은 사이트 안(site/app/lib)에 둔다.
set -euo pipefail
mkdir -p site/app/lib
cp site/icon-180.png site/app/icon-180.png

T="${RUNNER_TEMP:-/tmp}/aero-lib"; rm -rf "$T"; mkdir -p "$T"
(cd "$T" && npm pack pdfjs-dist@3.11.174 html5-qrcode@2.3.8 --silent >/dev/null)
mkdir -p "$T/pdf" "$T/qr"
tar -xzf "$T"/pdfjs-dist-3.11.174.tgz -C "$T/pdf"
tar -xzf "$T"/html5-qrcode-2.3.8.tgz -C "$T/qr"
cp "$T/pdf/package/build/pdf.min.js" site/app/lib/pdf.min.js
cp "$T/pdf/package/build/pdf.worker.min.js" site/app/lib/pdf.worker.min.js
QR=$(find "$T/qr/package" -name 'html5-qrcode.min.js' | head -1)
[ -n "$QR" ] || { echo "::error::html5-qrcode.min.js를 찾지 못했어요"; exit 1; }
cp "$QR" site/app/lib/html5-qrcode.min.js
# 처음 확인한 파일과 한 글자라도 다르면 멈춤 (LIB_SHA256 파일에 고정)
if [ -s .github/lib.sha256 ]; then
  (cd site/app/lib && sha256sum -c ../../../.github/lib.sha256)
else
  echo "::warning::lib.sha256이 아직 없어요. 아래 값을 .github/lib.sha256에 고정하세요"
fi
(cd site/app/lib && sha256sum pdf.min.js pdf.worker.min.js html5-qrcode.min.js)

# 홈 화면 아이콘·이름, '미리보기' 안내는 '웹' 안내로, 부품 주소는 같은 사이트로, CSP에서 cdnjs 빼기
sed -e 's#<meta charset="utf-8">#<meta charset="utf-8"><link rel="apple-touch-icon" href="icon-180.png"><meta name="apple-mobile-web-app-title" content="Aero"><meta name="apple-mobile-web-app-capable" content="yes">#' \
    -e 's/미리보기에서는/웹에서는/g' \
    -e "s#https://cdnjs.cloudflare.com/ajax/libs/html5-qrcode/2.3.8/html5-qrcode.min.js#lib/html5-qrcode.min.js#" \
    -e "s#https://cdnjs.cloudflare.com/ajax/libs/pdf.js/3.11.174/pdf.min.js#lib/pdf.min.js#" \
    -e "s#https://cdnjs.cloudflare.com/ajax/libs/pdf.js/3.11.174/pdf.worker.min.js#lib/pdf.worker.min.js#" \
    -e 's# https://cdnjs.cloudflare.com##g' \
  app/src/main/assets/index.html > site/app/index.html
grep -q 'apple-touch-icon' site/app/index.html
if grep -q 'cdnjs' site/app/index.html; then echo "::error::웹앱에 cdnjs 주소가 남아 있어요"; exit 1; fi
