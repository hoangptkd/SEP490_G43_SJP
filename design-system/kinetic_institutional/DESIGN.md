---
name: Kinetic Institutional
colors:
  surface: '#f8f9ff'
  surface-dim: '#cbdbf5'
  surface-bright: '#f8f9ff'
  surface-container-lowest: '#ffffff'
  surface-container-low: '#eff4ff'
  surface-container: '#e5eeff'
  surface-container-high: '#dce9ff'
  surface-container-highest: '#d3e4fe'
  on-surface: '#0b1c30'
  on-surface-variant: '#40474f'
  inverse-surface: '#213145'
  inverse-on-surface: '#eaf1ff'
  outline: '#707881'
  outline-variant: '#c0c7d1'
  surface-tint: '#006399'
  primary: '#00507d'
  on-primary: '#ffffff'
  primary-container: '#0369a1'
  on-primary-container: '#cbe4ff'
  inverse-primary: '#94ccff'
  secondary: '#006d30'
  on-secondary: '#ffffff'
  secondary-container: '#92f5a4'
  on-secondary-container: '#007233'
  tertiary: '#733f00'
  on-tertiary: '#ffffff'
  tertiary-container: '#955301'
  on-tertiary-container: '#ffdbbe'
  error: '#ba1a1a'
  on-error: '#ffffff'
  error-container: '#ffdad6'
  on-error-container: '#93000a'
  primary-fixed: '#cde5ff'
  primary-fixed-dim: '#94ccff'
  on-primary-fixed: '#001d32'
  on-primary-fixed-variant: '#004b74'
  secondary-fixed: '#95f8a7'
  secondary-fixed-dim: '#79db8d'
  on-secondary-fixed: '#00210a'
  on-secondary-fixed-variant: '#005323'
  tertiary-fixed: '#ffdcc1'
  tertiary-fixed-dim: '#ffb878'
  on-tertiary-fixed: '#2e1500'
  on-tertiary-fixed-variant: '#6c3a00'
  background: '#f8f9ff'
  on-background: '#0b1c30'
  surface-variant: '#d3e4fe'
typography:
  display-lg:
    fontFamily: Lexend
    fontSize: 48px
    fontWeight: '600'
    lineHeight: 56px
    letterSpacing: -0.02em
  headline-lg:
    fontFamily: Lexend
    fontSize: 32px
    fontWeight: '600'
    lineHeight: 40px
    letterSpacing: -0.01em
  headline-lg-mobile:
    fontFamily: Lexend
    fontSize: 24px
    fontWeight: '600'
    lineHeight: 32px
  headline-md:
    fontFamily: Lexend
    fontSize: 24px
    fontWeight: '500'
    lineHeight: 32px
  body-lg:
    fontFamily: Inter
    fontSize: 18px
    fontWeight: '400'
    lineHeight: 28px
  body-md:
    fontFamily: Inter
    fontSize: 16px
    fontWeight: '400'
    lineHeight: 24px
  body-sm:
    fontFamily: Inter
    fontSize: 14px
    fontWeight: '400'
    lineHeight: 20px
  label-md:
    fontFamily: Inter
    fontSize: 14px
    fontWeight: '600'
    lineHeight: 20px
  label-sm:
    fontFamily: Inter
    fontSize: 12px
    fontWeight: '500'
    lineHeight: 16px
    letterSpacing: 0.02em
  mono-sm:
    fontFamily: Inter
    fontSize: 13px
    fontWeight: '400'
    lineHeight: 18px
rounded:
  sm: 0.125rem
  DEFAULT: 0.25rem
  md: 0.375rem
  lg: 0.5rem
  xl: 0.75rem
  full: 9999px
spacing:
  base: 4px
  xs: 4px
  sm: 8px
  md: 16px
  lg: 24px
  xl: 32px
  gutter: 24px
  sidebar-width: 280px
  container-max: 1440px
---

## Brand & Style

This design system is built for high-stakes professional environments where clarity, efficiency, and trust are paramount. The brand personality is authoritative yet modern, positioning the recruitment portal as a sophisticated tool for enterprise talent acquisition.

The visual direction follows a **Modern Corporate** aesthetic. It prioritizes a highly organized information hierarchy, generous whitespace to reduce cognitive load during complex tasks, and subtle interactive cues that provide confidence without distraction. The style is systematic and utilitarian, utilizing crisp borders and a refined color palette to differentiate it from consumer-grade applications.

## Colors

The palette is anchored by **Trust Blue**, a deep, professional primary color used for core actions and brand identification. **Success Green** is reserved for positive outcomes, such as completed applications or successful hires.

The neutral scale utilizes **Slate Neutrals** to maintain a cool, balanced tone across the interface.
- **Primary (#0369A1):** Buttons, active states, and primary navigational elements.
- **Secondary/Success (#15803D):** Status indicators and completion confirmations.
- **Background (#F8FAFC):** A subtle off-white to reduce screen glare during extended usage.
- **Text:** Using Slate 900 (#0F172A) for high-contrast readability and Slate 600 (#475569) for secondary metadata.

## Typography

The typographic strategy employs a dual-font system to balance brand character with functional density. 

**Lexend** is used for headlines and branded touchpoints. Its geometric nature provides a modern, approachable feel to large-scale text. 

**Inter** is the workhorse for the UI. It is selected for its exceptional legibility at small sizes and high x-height, making it ideal for dense data tables, candidate profiles, and form labels. Use `body-sm` for most table content and `label-md` for buttons and navigation items to ensure a clear hierarchy.

## Layout & Spacing

The design system utilizes a **Fixed-Fluid Hybrid** model. Dashboards feature a fixed 280px sidebar on the left with a fluid content area that adheres to a 12-column grid.

- **Desktop (1280px+):** 12-column grid, 24px gutters, 32px outer margins.
- **Tablet (768px - 1279px):** 8-column grid, 16px gutters, 24px outer margins. Sidebar collapses to an icon-only rail or drawer.
- **Mobile (Under 768px):** 4-column grid, 16px gutters, 16px outer margins.

Auth screens and focused flows (like interview scheduling) use a centered 480px fixed-width container to minimize eye-scanning and increase focus. Spacing follows a 4px base unit to allow for the precision required in dense professional SaaS interfaces.

## Elevation & Depth

This design system uses **Tonal Layers** and **Low-Contrast Outlines** to define hierarchy, avoiding heavy shadows to maintain a clean, "flat-plus" appearance.

- **Level 0 (Background):** #F8FAFC. The foundation layer.
- **Level 1 (Cards/Surface):** #FFFFFF with a 1px border of #E2E8F0. This is the primary container for content.
- **Level 2 (Hover/Active):** A restrained shadow (0 4px 6px -1px rgb(0 0 0 / 0.1)) is applied only to interactive elements like cards or buttons when hovered to indicate "lift."
- **Level 3 (Modals/Popovers):** A more pronounced shadow (0 10px 15px -3px rgb(0 0 0 / 0.1)) to separate floating UI from the main application state.

Separation is primarily achieved through subtle background shifts and 1px borders rather than depth effects.

## Shapes

The shape language is precise and disciplined. Roundedness is kept minimal to reinforce a professional, "software-as-a-service" feel.

- **Controls (Buttons, Inputs, Selects):** 6px radius. This provides a soft touch while maintaining a sharp, technical appearance.
- **Containers (Cards, Modals, Banners):** 8px radius. This slight increase in rounding helps larger blocks of content feel distinct from the smaller UI controls contained within them.
- **Badges/Status Tags:** Fully rounded (pill-shaped) to distinguish them visually from interactive buttons.

## Components

### Buttons
Primary buttons use the Trust Blue fill with white text. Secondary buttons use a Slate 200 border with a Slate 900 text. Hover states involve a slight darkening of the fill or background.

### Dense Tables
The core of the recruitment portal. Row heights are kept at a compact 48px. Use 1px horizontal dividers in Slate 100. Column headers use `label-sm` in Slate 500, all-caps.

### Status Badges
Used for candidate stages (e.g., "Screening", "Interviewed"). These use a soft-fill approach: a 10% opacity background of the status color with a 100% opacity text of the same color (e.g., Success Green text on a very pale green background).

### Form Controls
Inputs feature a 1px Slate 300 border that shifts to Primary Blue on focus. Labels sit 4px above the input field using `label-md`. Support text or error messages appear 4px below.

### Side Navigation
Active items in the sidebar use a subtle background tint of Trust Blue (5-10% opacity) and a 3px vertical "active bar" on the far left edge of the menu item.