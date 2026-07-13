import React from 'react';
import type { StyleProp, ViewStyle } from 'react-native';
import {
  ArrowDown,
  ArrowLeftRight,
  ArrowRight,
  ArrowUp,
  Bell,
  Camera,
  Check,
  Circle,
  CircleAlert,
  CircleArrowDown,
  CircleArrowLeft,
  CircleArrowRight,
  CircleArrowUp,
  CircleCheck,
  CircleEllipsis,
  ChevronLeft,
  CircleUser,
  CircleX,
  Clock,
  Compass,
  Construction,
  CreditCard,
  Eye,
  EyeOff,
  Gift,
  GraduationCap,
  Hourglass,
  House,
  Image,
  Lightbulb,
  Lock,
  LockOpen,
  Mail,
  MailOpen,
  Receipt,
  Rocket,
  Send,
  ShieldCheck,
  Smartphone,
  Sparkles,
  Stethoscope,
  Target,
  TrendingUp,
  TriangleAlert,
  Trophy,
  Undo2,
  User,
  Users,
  Wallet,
  X,
  Zap,
} from 'lucide-react-native';

/**
 * Single icon set for the whole app (Lucide, via react-native-svg). Keys
 * mirror the legacy Ionicons name strings that call sites already used, so
 * this is a render-source swap only — no call-site name churn.
 */
const ICONS = {
  home: House,
  'home-outline': House,
  compass: Compass,
  'compass-outline': Compass,
  person: User,
  'person-circle': CircleUser,
  'person-circle-outline': CircleUser,
  'lock-closed': Lock,
  'lock-closed-outline': Lock,
  'lock-open-outline': LockOpen,
  'mail-outline': Mail,
  'mail-open-outline': MailOpen,
  'checkmark-circle': CircleCheck,
  'close-circle': CircleX,
  checkmark: Check,
  close: X,
  'construct-outline': Construction,
  'shield-checkmark-outline': ShieldCheck,
  'notifications-outline': Bell,
  sparkles: Sparkles,
  'flash-outline': Zap,
  'time-outline': Clock,
  'hourglass-outline': Hourglass,
  'rocket-outline': Rocket,
  'warning-outline': TriangleAlert,
  'arrow-undo-outline': Undo2,
  'arrow-up-circle-outline': CircleArrowUp,
  'arrow-down-circle-outline': CircleArrowDown,
  'arrow-forward-circle-outline': CircleArrowRight,
  'arrow-back-circle-outline': CircleArrowLeft,
  'swap-horizontal-outline': ArrowLeftRight,
  'ellipse-outline': Circle,
  'people-circle-outline': Users,
  'people-outline': Users,
  'gift-outline': Gift,
  'alert-circle-outline': CircleAlert,
  'wallet-outline': Wallet,
  'card-outline': CreditCard,
  'phone-portrait-outline': Smartphone,
  'trophy-outline': Trophy,
  'arrow-down': ArrowDown,
  'arrow-forward': ArrowRight,
  'arrow-up': ArrowUp,
  'receipt-outline': Receipt,
  'bulb-outline': Lightbulb,
  'camera-outline': Camera,
  'images-outline': Image,
  'school-outline': GraduationCap,
  'medkit-outline': Stethoscope,
  'ellipsis-horizontal-circle-outline': CircleEllipsis,
  'paper-plane-outline': Send,
  eye: Eye,
  'eye-off': EyeOff,
  'chevron-back': ChevronLeft,
  target: Target,
  'trending-up': TrendingUp,
} as const;

export type IconName = keyof typeof ICONS;

interface IconProps {
  name: IconName;
  size: number;
  color: string;
  strokeWidth?: number;
  style?: StyleProp<ViewStyle>;
}

export function Icon({ name, size, color, strokeWidth = 2, style }: IconProps) {
  const LucideIcon = ICONS[name];
  return <LucideIcon size={size} color={color} strokeWidth={strokeWidth} style={style} />;
}
