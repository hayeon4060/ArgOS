/**
 * 5공장 공정 배치도 그림(assets/images/plant-map.svg)의 좌표 정보
 * - 화면설계안 js/lib.js plantMap() 과 같은 계산으로 만든 값 (그림을 바꾸면 같이 다시 만들어야 함)
 * - VIEWBOX: 그림의 viewBox, center: 이름표 위치, camera: 열화상 카메라 위치 (그림 좌표)
 * - 공정 코드(hall)는 그림 안 구역 이름 — 공정 ID 와의 짝은 PlantMap.vue 의 HALL_BY_CODE
 */
export const VIEWBOX = {"x":-283.7,"y":-95.3,"w":1781.2,"h":967.5}

export const HALLS = {
  "press": {
    "name": "프레스 공정",
    "ir": "IR-02",
    "center": [
      221.9,
      114.9
    ],
    "camera": [
      386.9,
      198.5
    ]
  },
  "body": {
    "name": "차체 공정",
    "ir": "IR-03",
    "center": [
      945.1,
      310.4
    ],
    "camera": [
      1295.6,
      444.2
    ]
  },
  "assy": {
    "name": "의장 공정",
    "ir": "IR-04",
    "center": [
      209.4,
      367.8
    ],
    "camera": [
      493.2,
      487
    ]
  },
  "paint": {
    "name": "도장 공정",
    "ir": "IR-01",
    "center": [
      932.6,
      563.3
    ],
    "camera": [
      1160.7,
      667.5
    ]
  }
}
