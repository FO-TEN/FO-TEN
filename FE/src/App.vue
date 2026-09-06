<script setup>
import { useLocaleStore } from './stores/locale'
const locale = useLocaleStore()
</script>

<template>
  <!-- 디자인이 375px 모바일 프레임이다. 넓은 화면에서는 가운데에 세운다.
       페이지 전환 애니메이션은 두지 않는다 — 시안에 없고, 탭이 백그라운드일 때 transitionend 가
       오지 않아 화면이 투명한 채로 멈추는 경우가 있었다. -->
  <div class="phone" :lang="locale.htmlLang">
    <router-view />
  </div>
</template>
