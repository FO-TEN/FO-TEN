<script setup>
import hero from '../../assets/img/poten_hero.png'
import wink from '../../assets/img/poten_wink.png'
import happy from '../../assets/img/poten_happy.png'
import sorry from '../../assets/img/poten_sorry.png'
import surprised from '../../assets/img/poten_surprised.png'
import profile from '../../assets/img/foten_profile.png'

// Figma Avatar/포텐이(68:45) — 원본 캐릭터 히어로 크롭, 배경 투명, FIT.
const props = defineProps({
  size: { type: Number, default: 36 },
  mood: { type: String, default: 'hero' }, // hero | wink | happy | sorry | surprised | profile
})
const src = { hero, wink, happy, sorry, surprised, profile }
</script>

<template>
  <img
    :src="src[props.mood] || hero"
    alt=""
    class="poten"
    :style="{ width: props.size + 'px', height: props.size + 'px' }"
  />
</template>

<style scoped>
.poten {
  object-fit: contain;
  flex: 0 0 auto;
}
</style>
