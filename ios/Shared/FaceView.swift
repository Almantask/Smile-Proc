import SmileCore
import SwiftUI

public struct FaceView: View {
    public var mood: Mood

    public init(mood: Mood) {
        self.mood = mood
    }

    public var body: some View {
        Canvas { context, size in
            let side = min(size.width, size.height)
            let origin = CGPoint(x: (size.width - side) / 2, y: (size.height - side) / 2)
            let rect = CGRect(origin: origin, size: CGSize(width: side, height: side)).insetBy(dx: side * 0.08, dy: side * 0.08)
            let stroke = StrokeStyle(lineWidth: max(1.5, side * 0.045), lineCap: .round, lineJoin: .round)
            context.stroke(Path(ellipseIn: rect), with: .foreground, style: stroke)
            let eyeRadius = rect.width * 0.045
            let eyeY = rect.minY + rect.height * 0.38
            for fraction in [0.35, 0.65] as [CGFloat] {
                let eye = CGRect(
                    x: rect.minX + rect.width * fraction - eyeRadius,
                    y: eyeY,
                    width: eyeRadius * 2,
                    height: eyeRadius * 2
                )
                context.fill(Path(ellipseIn: eye), with: .foreground)
            }
            var mouth = Path()
            let left = CGPoint(x: rect.minX + rect.width * 0.28, y: rect.minY + rect.height * 0.66)
            let right = CGPoint(x: rect.minX + rect.width * 0.72, y: rect.minY + rect.height * 0.66)
            let controlY: CGFloat
            switch mood {
            case .low:
                controlY = rect.minY + rect.height * 0.84
            case .okay:
                controlY = left.y
            case .good:
                controlY = rect.minY + rect.height * 0.50
            }
            mouth.move(to: left)
            mouth.addQuadCurve(to: right, control: CGPoint(x: rect.midX, y: controlY))
            context.stroke(mouth, with: .foreground, style: stroke)
        }
        .accessibilityHidden(true)
    }
}
