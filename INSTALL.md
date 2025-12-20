# 설치 및 실행 가이드 (Installation Guide)

이 문서는 `cadp-web-converter` 프로젝트를 GitHub에서 다운로드하여 설치, 빌드 및 실행하는 방법과 Windows 환경에서 외부 접속을 위한 설정 방법을 설명합니다.

## 1. 사전 요구 사항 (Prerequisites)

*   **Java Development Kit (JDK) 11** 이상
*   **Maven** (빌드 도구)
*   **Git** (소스 코드 다운로드용)

## 2. 프로젝트 다운로드 (Clone)

터미널(또는 명령 프롬프트)을 열고 아래 명령어를 입력하여 프로젝트를 다운로드합니다.

```bash
git clone https://github.com/sjrhee/cadp-web-converter.git
cd cadp-web-converter
```

## 3. 설정 (Configuration)

프로젝트 루트 경로에 `.env` 파일을 생성하고, CADP 연결 정보를 설정해야 합니다.

**`.env` 파일 예시:**
```properties
CADP_HOST=192.168.10.100
CADP_PORT=443
CADP_TOKEN=your_secure_token_here
CADP_USER=admin
CADP_POLICY=default_policy
```

## 4. 빌드 및 실행 (Build & Run)

운영체제에 맞는 방법으로 애플리케이션을 빌드하고 실행합니다.

### Linux

프로젝트에 포함된 스크립트를 사용하면 편리합니다.

```bash
# 실행 권한 부여 (최초 1회)
chmod +x build_and_run.sh

# 빌드 및 실행
./build_and_run.sh
```

애플리케이션이 정상적으로 실행되면 브라우저에서 `http://192.168.100.13:8088`로 접속할 수 있습니다.

---

## 5. Windows에서 외부 접속 허용 (Port Forwarding)

Windows 서버에서 애플리케이션을 실행하고, 외부(다른 PC)에서 이 서버(포트 8088)로 접속하려면 **방화벽 설정**과 **포트 포워딩**이 필요할 수 있습니다.

### 방법 A: Windows 방화벽 인바운드 규칙 추가 (기본)

대부분의 경우 방화벽만 열어주면 외부 접속이 가능합니다.

1.  `Windows Defender 방화벽` 실행 -> `고급 설정` -> `인바운드 규칙`.
2.  `새 규칙` 클릭 -> `포트` 선택 -> `다음`.
3.  `TCP` 선택, `특정 로컬 포트`에 `8088` 입력 -> `다음`.
4.  `연결 허용` 선택 -> `다음`.
5.  도메인, 개인, 공용 모두 체크(환경에 따라 조정) -> `다음`.
6.  이름에 `CADP Web Converter (8088)` 입력 후 `마침`.

### 방법 B: netsh를 이용한 포트 포워딩 (WSL 또는 특정 네트워크 환경)

만약 WSL(리눅스 서브시스템)이나 특정 가상 환경에서 실행 중이거나, 로컬호스트 루프백 문제로 외부 접속이 안 될 경우 `netsh` 명령어를 사용하여 포트를 포워딩할 수 있습니다.

**관리자 권한**으로 PowerShell 또는 명령 프롬프트를 실행한 후 아래 명령어를 입력하세요.

```powershell
# 포트 포워딩 규칙 추가 (모든 IP의 8088 포트를 로컬호스트 8088로 연결)
netsh interface portproxy add v4tov4 listenport=8088 listenaddress=0.0.0.0 connectport=8088 connectaddress=127.0.0.1

# 설정 확인
netsh interface portproxy show all

# (참고) 규칙 삭제 시 명령어
# netsh interface portproxy delete v4tov4 listenport=8088 listenaddress=0.0.0.0
```

이제 외부 PC에서 `http://[Windows_IP_주소]:8088`로 접속하여 애플리케이션을 사용할 수 있습니다.
