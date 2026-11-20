# Postman 컬렉션

연계 서버 API 95개를 화면별 폴더(18개)로 묶은 컬렉션.

## 불러오기
1. Postman → **Import** → `ArgOS-gateway.postman_collection.json`
2. 컬렉션 변수 `baseUrl` 확인 (기본 `http://localhost:8080`)
3. `01 로그인 · 내 정보` → `POST /auth/login` 을 보내면 `accessToken` 이 자동 저장됨 (기본 계정 admin)
4. 나머지 요청은 그 토큰으로 바로 보낼 수 있음

경로 변수 기본값: `factoryId=5`, `processId=12`, `siteId=1`, 나머지는 `1`. 선택 쿼리는 꺼져 있음(체크하면 켜짐).

## 팀 공유
Postman 워크스페이스(Team)에 컬렉션을 올리고 팀원을 초대한다. 요청마다 설명·예시 응답(Save Response)을 붙이면 문서처럼 보인다.

## 다시 만들기 (API 가 바뀌었을 때)
서버를 켠 뒤:

```
python make_collection.py                              # http://localhost:8080/v3/api-docs
python make_collection.py http://localhost:18080/v3/api-docs
```

또는 Postman Import 에 `http://localhost:8080/v3/api-docs` 주소를 바로 넣어도 된다 (로그인 자동 저장 스크립트는 빠짐).
