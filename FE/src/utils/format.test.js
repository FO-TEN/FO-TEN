import { describe, test, expect } from 'vitest'
import { comma, won, signed, dotDate, dotMonth, ym } from './format.js'

describe('comma', () => {
    test('천 단위 콤마를 찍는다', () => {
        expect(comma(1234567)).toBe('1,234,567')
    })

    test('0은 그대로 0을 반환한다', () => {
        expect(comma(0)).toBe('0')
    })

    test('null·undefined·빈 문자열은 대시(—)를 반환한다', () => {
        expect(comma(null)).toBe('—')
        expect(comma(undefined)).toBe('—')
        expect(comma('')).toBe('—')
    })
})

describe('won', () => {
    test('₩ 기호와 콤마를 함께 붙인다', () => {
        expect(won(12236000)).toBe('₩12,236,000')
    })

    test('값이 없으면 대시를 반환한다', () => {
        expect(won(null)).toBe('—')
    })
})

describe('signed', () => {
    test('양수는 + 기호를 붙인다', () => {
        expect(signed(500)).toBe('+500')
    })

    test('음수는 − 기호를 붙인다 (하이픈이 아니라 마이너스 기호)', () => {
        expect(signed(-500)).toBe('−500')
    })

    test('0은 부호 없이 0을 반환한다', () => {
        expect(signed(0)).toBe('0')
    })

    test('값이 없으면 대시를 반환한다', () => {
        expect(signed(null)).toBe('—')
    })
})

describe('dotDate', () => {
    test('연-월-일을 점으로 구분한다', () => {
        expect(dotDate('2024-09-15')).toBe('2024.09.15')
    })

    test('일(day)이 없으면 연.월까지만 반환한다', () => {
        expect(dotDate('2024-09')).toBe('2024.09')
    })

    test('값이 없으면 대시를 반환한다', () => {
        expect(dotDate(null)).toBe('—')
        expect(dotDate('')).toBe('—')
    })
})

describe('dotMonth', () => {
    test('연-월만 추출해 점으로 구분한다', () => {
        expect(dotMonth('2024-09-15')).toBe('2024.09')
    })

    test('값이 없으면 대시를 반환한다', () => {
        expect(dotMonth(null)).toBe('—')
    })
})

describe('ym', () => {
    test('연-월 문자열을 숫자 객체로 변환한다', () => {
        expect(ym('2026-09')).toEqual({ year: 2026, month: 9 })
    })
})