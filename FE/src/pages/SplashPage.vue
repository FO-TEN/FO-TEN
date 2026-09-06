<script setup>
import { onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { useLocaleStore } from '../stores/locale'
import hero from '../assets/img/poten_hero.png'
import logo from '../assets/img/foten_logo.png'
import ellipse from '../assets/img/splash_ellipse.svg'
import s1 from '../assets/img/spark_1.svg'
import s2 from '../assets/img/spark_2.svg'
import s3 from '../assets/img/spark_3.svg'
import s4 from '../assets/img/spark_4.svg'
import s5 from '../assets/img/spark_5.svg'
import s6 from '../assets/img/spark_6.svg'
import s7 from '../assets/img/spark_7.svg'

// Figma 00a_스플래시(217:2942). 1.5초 뒤 자동 이동 — 세션이 살아 있으면 홈/온보딩, 아니면 로그인.
const router = useRouter()
const auth = useAuthStore()
const locale = useLocaleStore()

// 시안의 절대 좌표 그대로 (375x812 기준)
const sparks = [
  { src: s1, x: 190, y: 90, s: 8 },
  { src: s2, x: 280, y: 470, s: 10 },
  { src: s3, x: 36, y: 470, s: 12 },
  { src: s4, x: 320, y: 330, s: 16 },
  { src: s5, x: 60, y: 300, s: 10 },
  { src: s6, x: 250, y: 140, s: 12 },
  { src: s7, x: 120, y: 120, s: 18 },
]

let timer
onMounted(() => {
  timer = setTimeout(async () => {
    const ok = await auth.ensureSession()
    if (!ok) return router.replace({ name: 'login' })
    return router.replace(auth.onboarding?.completed ? { name: 'home' } : { name: 'onboarding', params: { step: 1 } })
  }, 1500)
})
onBeforeUnmount(() => clearTimeout(timer))
</script>

<template>
  <main class="splash">
    <img
      v-for="(sp, i) in sparks"
      :key="i"
      :src="sp.src"
      alt=""
      class="spark"
      :style="{ left: sp.x + 'px', top: sp.y + 'px', width: sp.s + 'px', height: sp.s + 'px' }"
    />
    <img :src="ellipse" alt="" class="shadow" />
    <div class="center">
      <img :src="hero" alt="" class="hero" />
      <img :src="logo" alt="FO:TEN" class="logo" />
      <p class="tagline">{{ locale.t('splash.tagline1') }}<br />{{ locale.t('splash.tagline2') }}</p>
    </div>
    <div class="foot">
      <p class="f1">{{ locale.t('splash.foot1') }}</p>
      <p class="f2">{{ locale.t('splash.foot2') }}</p>
    </div>
  </main>
</template>

<style scoped>
.splash {
  position: relative;
  min-height: 100dvh;
  background: linear-gradient(180deg, #fffcf0 0%, #fff0b8 100%);
  overflow: hidden;
}
.spark {
  position: absolute;
}
.shadow {
  position: absolute;
  left: 127.5px;
  top: 377px;
  width: 120px;
  height: 18px;
}
.center {
  position: absolute;
  left: 0;
  right: 0;
  top: 240px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 20px;
}
.hero {
  width: 180px;
  height: 142px;
  object-fit: contain;
}
.logo {
  width: 156px;
  height: 40px;
  object-fit: contain;
}
.tagline {
  text-align: center;
  font-size: 17px;
  font-weight: 500;
  line-height: 1.5;
  color: var(--gray-700);
}
.foot {
  position: absolute;
  left: 0;
  right: 0;
  top: 710px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  line-height: 1.5;
  text-align: center;
}
.f1 {
  color: var(--gray-500);
}
.f2 {
  font-weight: 700;
  color: var(--on-primary);
}
</style>
