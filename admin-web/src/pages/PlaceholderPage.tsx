import { Result } from "antd";

type Props = {
  title: string;
  description: string;
};

export function PlaceholderPage({ title, description }: Props) {
  return <Result status="info" title={title} subTitle={description} />;
}
