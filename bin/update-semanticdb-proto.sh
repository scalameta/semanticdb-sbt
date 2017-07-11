out=sbthost/nsc/src/main/protobuf/semanticdb.proto
url=https://raw.githubusercontent.com/scalameta/scalameta/master/scalameta/semantic/shared/src/main/protobuf/semanticdb.proto
wget -O $out $url
echo "// DO NOT EDIT! This file must match\n// $url\n$(cat $out)" > $out

