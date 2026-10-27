import { scoreTone } from "@/lib/format";

const toneMap = {
  excellent: { stroke: "#15803d", bg: "#f0f0e8", label: "字面覆盖较高" },
  good: { stroke: "#1d4ed8", bg: "#f0f0e8", label: "检查经历依据" },
  warning: { stroke: "#f97316", bg: "#f0f0e8", label: "部分表达待核实" },
  risk: { stroke: "#dc2626", bg: "#f0f0e8", label: "较多表达待核实" },
};

export function ScoreRing({
  label,
  score,
  size = 132,
}: {
  label: string;
  score: number;
  size?: number;
}) {
  const radius = (size - 16) / 2;
  const circumference = 2 * Math.PI * radius;
  const offset = circumference - (Math.max(0, Math.min(score, 100)) / 100) * circumference;
  const tone = toneMap[scoreTone(score)];

  return (
    <div className="flex items-center gap-4 border border-black bg-[#f0f0e8] p-4 shadow-sw-sm">
      <div className="relative" style={{ height: size, width: size }}>
        <svg height={size} width={size}>
          <circle cx={size / 2} cy={size / 2} fill={tone.bg} r={radius} stroke="#000000" strokeWidth="1" />
          <circle
            cx={size / 2}
            cy={size / 2}
            fill="transparent"
            r={radius - 7}
            stroke="#d8d8d2"
            strokeWidth="10"
          />
          <circle
            cx={size / 2}
            cy={size / 2}
            fill="transparent"
            r={radius - 7}
            stroke={tone.stroke}
            strokeDasharray={circumference}
            strokeDashoffset={offset}
            strokeLinecap="square"
            strokeWidth="10"
            style={{ transform: "rotate(-90deg)", transformOrigin: "50% 50%" }}
          />
        </svg>
        <div className="absolute inset-0 flex flex-col items-center justify-center">
          <span className="text-4xl font-black leading-none">{score}</span>
          <span className="font-mono text-[10px] font-black uppercase tracking-[0.18em]">/100</span>
        </div>
      </div>
      <div>
        <p className="font-mono text-xs font-bold uppercase tracking-wide text-[#1d4ed8]">{label}</p>
        <p className="mt-2 font-serif text-2xl font-semibold">{tone.label}</p>
        <p className="mt-2 max-w-44 font-mono text-xs leading-5 text-[#6b7280]">关键词及规则估算；不代表能力、投递资格或录用概率。</p>
      </div>
    </div>
  );
}
